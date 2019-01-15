# Anti-X-Ray-1
My first implementation of an anti x-ray plug-in for minecraft
I have 2 variants of this plug-in, this is the first variant.
The difference between the variants is that this one has better performance than the second one, 
but consumes a lot of disk space and some memory in return. It is also more 'dangerous' since almost all ores will be lost if things
go wrong.

The plug-in works like this:
Upon world generation, all ores that are not exposed to air (and some other half blocks), will be removed from the world and stored in
the file system of the server. When a block next to a 'hidden' ore is broken, it will be placed back in the world.
This is generally good for performance because there is no need for expensive packet filtering. Also, cave mining will be preserved
since ores next to air will remain where they are.
