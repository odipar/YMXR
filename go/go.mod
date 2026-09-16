module github.com/odipar/ymxr/go

go 1.26

// The two libraries this converts through: DTX's packer with its
// twenty-two images, and YMXS's tune data structure. Both stand at the
// releases the pom names for their Java artifacts.
require (
	github.com/odipar/dtx/go v0.11.3
	github.com/odipar/ymxs/go v0.3.4
)

require github.com/odipar/st4/go v0.1.4 // indirect
