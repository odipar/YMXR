module github.com/odipar/ymxr/go

go 1.26

// The two libraries this converts through: DTX's packer with its forty
// images, and YMXS's tune data structure. Both are at the
// releases the pom names for their Java artifacts.
require (
	github.com/odipar/dtx/go v0.12.0
	github.com/odipar/ymxs/go v0.5.0
)

require github.com/odipar/st4/go v0.1.11 // indirect

replace github.com/odipar/ymxs/go => ../../YMXS/go
