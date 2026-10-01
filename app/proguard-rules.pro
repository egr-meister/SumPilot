# SumPilot R8 rules. Minification is disabled by default (sumpilot.minify=false) until a signed,
# non-minified release has been verified on a device. Room, Compose and DataStore ship their own
# consumer rules; nothing here uses reflection on app classes.

# Keep line numbers for readable crash traces (mapping.txt is archived by CI).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
