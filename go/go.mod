module github.com/odipar/ymxr/go

go 1.26

// The three libraries this converts through. DTX's packer with its
// twenty-two images, and YMXS's tune data structure, stand at the releases
// the pom names for their Java artifacts. YMX's .ymx reader has no Java
// artifact: the Java tools here run ymx-dump in its place, and a tool
// built from this tree calls no program at all.
require (
	github.com/odipar/dtx/go v0.9.0
	github.com/odipar/ymx/go v0.1.0
	github.com/odipar/ymxs/go v0.3.1
)
