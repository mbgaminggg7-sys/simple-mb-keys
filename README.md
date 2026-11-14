# Simple MB Keys (Fabric 1.21.11)

Bind more than one key to the same action. Original code, written from scratch.

## In game
Options > Controls > Key Binds has a new **MB Keys** button (top left).
In that screen, each action has:
- `+`  add another key (then press it; Esc cancels)
- click a key button to change it
- `X`  remove that key
Your normal keys still work as usual; these are extras on top.
Also: `/mbkeys` opens the screen. Settings are saved in config/mbkeys.json.

## Get the .jar without installing anything (GitHub)
1. Make a free GitHub repo and upload everything in this folder (including the hidden .github folder).
2. Open the repo > Actions tab > the "build" run > download the **simple-mb-keys** artifact.
3. Unzip it: the .jar inside goes in your mods folder (with Fabric API).

## Or build locally
Needs JDK 21 and Gradle (or the wrapper from fabricmc.net/develop/template).
Run `gradle build` and take build/libs/simple-mb-keys-1.0.0.jar.
If a version says "not found", copy current numbers from fabricmc.net/develop into gradle.properties.
Edit "authors" in src/main/resources/fabric.mod.json.
