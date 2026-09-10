module github.com/odipar/ymxr

go 1.26

// The two libraries this converts through, taken from the checkouts beside
// this one, as the Maven build takes them from the same two.
replace dtx => ../../DTX/go

replace github.com/odipar/ymxs => ../../YMXS/go

require dtx v0.0.0-00010101000000-000000000000

require github.com/odipar/ymxs v0.0.0-00010101000000-000000000000 // indirect
