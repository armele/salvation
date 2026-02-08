#Entity Class
Create the entity class in salvationmod\ntity
For the Corrupted line, use an existing entity as a model.
Add the entity to ModEntityTypes
Add the entity to SalvationMod.onEntityAttributes
Add the entity to SalvationMod.onRegisterRenderers
Add the entity to SalvationMod.onRegisterLayerDefinitions
Add the entity to corrupted_spawns.json so it will spawn in the world.
Add the entity to corrupted_entity.json so our spawn control will find it.

#Model Definition
Create a model definition file at salvationmod\client\render\model
For the "corrupted" line:
- Find the vanilla texture
- Corrupted "base" model in blockbench 
- Convert it to a Modded Entity type
- Set the texture size appropriately to the vanilla texture size
- Fit the body parts to the vanilla texture
- Export as Java
- Replace the createBodyLayer() of the render class with the exported equivalent.

#Rendering:
Create a render class in salvationmod\client\render
Specify the right texture and layer location.

#Textures:
The java class specifies the top left corner of the north face as the texture offset. From there, the face layout is:

 TB 
ENWS

For the "corrupted" line:
- Find the vanilla texture
- Reskin the texture with corruption