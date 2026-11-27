module github.com/odipar/ymxr/go

go 1.26

// The two libraries this converts through: DTX's packer with its forty
// images, and YMXS's tune data structure. Both are at the
// releases the pom names for their Java artifacts.
require (
	github.com/odipar/dtx/go v0.12.0
	github.com/odipar/ymxs/go v0.12.0-SNAPSHOT
)

// The YMXS branch of structure 12, until its release (issue #412).
replace github.com/odipar/ymxs/go => /private/tmp/claude-504/-Users-rapido-git-YMX/ac591213-b3f7-485b-b3f4-9c337eee3e95/scratchpad/ymxs-w/go

require github.com/odipar/st4/go v0.1.11 // indirect
