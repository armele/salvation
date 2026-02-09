#Entity Class
Create the entity class in salvationmod\entity
For the Corrupted line, use an existing entity as the basis.
- Adjust the monster attributes
- Update the sounds
- Update the FollowAnimalGoal
Add the entity to ModEntityTypes
- Change the heights to creature-reasonable values.
Add the entity to SalvationMod:
- onEntityAttributes
- onRegisterRenderers
- onRegisterLayerDefinitions
Add the entity to these tags:
- corrupted_spawns.json (if you want it to spawn in the world).
- corrupted_entity.json so our spawn control will find it.
- corruptable_entity (the original vanilla version)
- the appropriate mapping file in salvation_cure_mappings

#Model Definition
Create a model definition file at salvationmod\client\render\model
For the "corrupted" line:
- Find the vanilla texture
- Corrupted "base" model in blockbench 
- Convert it to a Modded Entity type
- Set the texture size appropriately to the vanilla texture size
- Fit the body parts to the vanilla texture
- Export as Java

#Rendering:
Create a render class in salvationmod\client\render
Specify the right layer location and texture file
Insert the bockbench createBodyLayer() version for the model itself.

#Textures:
The java class specifies the top left corner of the north face as the texture offset. From there, the face layout is:

 TB 
ENWS

For the "corrupted" line:
- Find the vanilla texture (in the Minecraft jar)
- Reskin the texture with corruption

#Exteretio Integration
Find the vanilla equivalent in the biome definitions and swap them for their corrupted versions.