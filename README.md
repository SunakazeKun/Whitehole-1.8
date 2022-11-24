# Whitehole Despaghettification
![Editing Fluffy Bluff Galaxy](http://aurumsmods.com/res/img/programs/Whitehole.png)
**Whitehole Despaghettification** is a rewrite of *Whitehole*, a level editor for *Super Mario Galaxy* and *Super Mario Galaxy 2* that is almost 10 years old. This is an experimental fork that aims to make the program more stable while newer editors are being developed, such as Blackhole or Takochu. It removes a lot of unused code and broken features that were introduced between versions 1.4 and 1.7. Several outdated or questionable parts of the code have been upgraded, but there's still a lot more waiting to be improved. Major new additions include support for the [new object database](https://github.com/SunakazeKun/galaxydatabase), simple galaxy names that are displayed on the main window and a completely new Light Mode and Dark Mode UI. It is also fully compatible with all of SMG1's stages. Tons of bugs introduced in older versions of Whitehole have been fixed as well.

As of now, there is no proper RC build available, however, experimental pre-release builds can be found on the [releases page](https://github.com/SunakazeKun/Whitehole-Despaghettification/releases). It runs off **Java 11** due to JOGL being outdated. However, the program runs fine on newer Java versions as well, but requires the tool to be started using this command: ```java --add-exports=java.desktop/sun.awt=ALL-UNNAMED -Dsun.java2d.uiScale=1.0 -jar Whitehole.jar```. Alternatively, simply run the `Whitehole.bat` file included.

## Libraries
- **jogamp**: https://jogamp.org/
- **gluegen**: https://jogamp.org/gluegen/www/
- **org.json**: https://github.com/stleary/JSON-java
- **flatlaf**: https://github.com/JFormDesigner/FlatLaf
