package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"os"

	"github.com/odipar/dtx/go/st4"
	"github.com/odipar/ymxr/go/internal/cli/ymxstoprg"
)

// The runner sends records prefixed with their length as a big-endian
// long. Each body starts with three longs, unit, ring and signed loop.
// Jobs then carry column bytes. Results carry the column hash and ST4 data.
const (
	packedFile = "/.ymxr-packed-columns"
	maxPlan    = 32 << 20
	settings   = 12
)

func columnTool(name string) bool {
	var err error
	switch name {
	case "_ymxr-plan-prg":
		os.Args = os.Args[1:]
		err = planColumns()
	case "_ymxr-pack-columns":
		err = packColumns()
	case "_ymxr-assemble-prg":
		os.Args = os.Args[1:]
		err = assembleColumns()
	default:
		return false
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(2)
	}
	return true
}

func planColumns() error {
	var plan bytes.Buffer
	err := ymxstoprg.Plan(func(column []byte, unit, ring, loop int) error {
		if plan.Len()+4+settings+len(column) > maxPlan {
			return errors.New("column jobs exceed the worker plan limit")
		}
		var header [4 + settings]byte
		binary.BigEndian.PutUint32(header[0:], uint32(settings+len(column)))
		binary.BigEndian.PutUint32(header[4:], uint32(unit))
		binary.BigEndian.PutUint32(header[8:], uint32(ring))
		binary.BigEndian.PutUint32(header[12:], uint32(loop))
		plan.Write(header[:])
		plan.Write(column)
		return nil
	})
	if err != nil {
		return err
	}
	_, err = plan.WriteTo(os.Stdout)
	return err
}

func packColumns() error {
	jobs, err := io.ReadAll(io.LimitReader(os.Stdin, maxPlan+1))
	if err != nil {
		return err
	}
	if len(jobs) > maxPlan {
		return errors.New("column jobs exceed the worker plan limit")
	}
	packer := st4.Packer{}
	return readRecords(jobs, settings+1, func(job []byte) error {
		unit, ring, loop := readSettings(job)
		column := job[settings:]
		packed, err := packer.Pack(column, unit, ring, loop)
		if err != nil {
			return err
		}
		hash := sha256.Sum256(column)
		var length [4]byte
		binary.BigEndian.PutUint32(length[:], uint32(settings+len(hash)+len(packed)))
		for _, part := range [][]byte{length[:], job[:settings], hash[:], packed} {
			if _, err := os.Stdout.Write(part); err != nil {
				return err
			}
		}
		return nil
	})
}

func assembleColumns() error {
	data, err := os.ReadFile(packedFile)
	if err != nil {
		return err
	}
	packed := packedColumns{}
	err = readRecords(data, settings+sha256.Size+1, func(record []byte) error {
		unit, ring, loop := readSettings(record)
		hash := [sha256.Size]byte(record[settings : settings+sha256.Size])
		packed[columnKey{hash, unit, ring, loop}] = record[settings+sha256.Size:]
		return nil
	})
	if err != nil {
		return err
	}
	ymxstoprg.Run(packed)
	return nil
}

type columnKey struct {
	hash             [sha256.Size]byte
	unit, ring, loop int
}

type packedColumns map[columnKey][]byte

func (p packedColumns) Pack(column []byte, unit, ring, loop int) ([]byte, error) {
	if bytes, ok := p[columnKey{sha256.Sum256(column), unit, ring, loop}]; ok {
		return bytes, nil
	}
	return (st4.Packer{}).Pack(column, unit, ring, loop)
}

func (packedColumns) Copies() bool { return false }

func readSettings(record []byte) (int, int, int) {
	return int(binary.BigEndian.Uint32(record)), int(binary.BigEndian.Uint32(record[4:])),
		int(int32(binary.BigEndian.Uint32(record[8:])))
}

func readRecords(data []byte, minimum int, visit func([]byte) error) error {
	for len(data) > 0 {
		if len(data) < 4 {
			return errors.New("short column record length")
		}
		length := int(binary.BigEndian.Uint32(data))
		data = data[4:]
		if length < minimum || length > len(data) {
			return errors.New("invalid column record length")
		}
		if err := visit(data[:length]); err != nil {
			return err
		}
		data = data[length:]
	}
	return nil
}
