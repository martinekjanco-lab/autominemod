AUTOMINE - Fabric 1.21.11 (client-side mod)

USE
  /mine <block>   set the block to mine, e.g.  /mine red_sand   (tab-complete works)
  X               toggle bot.  Chat says "Automine on" / "Automine bot stopped"
  (change the key in Options > Controls > Key Binds > Automine)

The bot switches to the fastest hotbar tool for that block (shovel for sand/dirt/gravel),
turns its head smoothly, walks + jumps toward the nearest exposed block, and holds
left-click until it breaks. It stops if you take damage / get below 3 hearts,
or if no matching block is within ~20 blocks for 5 seconds.

BUILD (needs Java 21 + Gradle 9.x, or just open the folder in IntelliJ IDEA)
  gradle build
  -> jar is in build/libs/automine-1.0.0.jar  (NOT the -sources jar)
Put the jar in .minecraft/mods together with Fabric API 0.141.3+1.21.11 (or newer for 1.21.11).

If gradle says a version doesn't exist, edit gradle.properties with the numbers from https://fabricmc.net/develop
