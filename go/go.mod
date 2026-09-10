module github.com/odipar/ymxr/go

go 1.26

// The two libraries this converts through, at the releases the pom names
// for their Java artifacts: the packer and the twenty-two images from DTX,
// the tune data structure from YMXS.
require (
	github.com/odipar/dtx/go v0.9.0
	github.com/odipar/ymxs/go v0.1.0
)
