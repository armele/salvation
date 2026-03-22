/give @s minecraft:structure_block

Use place one structure block just outside the structure to be saved.
Provide a name, and set it as a "save" block

Use a second structure block to mark the opposite corner.
Set it to corner mode and name it to match the first block. Name should be of the style <modname>:structure_name

On the "save" block, detect the structure, then save.  The save will be written to runs\client\saves\<World Name>\generated\<modname>\structures\<structure_name.nbt>

The structure being saved must include a jigsaw block. (/give @s minecraft:jigsaw)
The jigsaw block will be the effective placement point of the structure. For a structure with a "basement", you would then want your jigsaw box elevated from the bottom of the overall structure by the depth of the basement.

