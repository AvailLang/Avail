Metadata:
1. #modules
2. For each module,
   1. moduleArchive


ModuleArchive:
1. UTF8 rootRelativeName
2. digestCache size
3. For each cached digest,
   1. timestamp (long)
   2. digest (32 bytes)
4. #versions
5. For each version,
   1. ModuleVersionKey
   2. ModuleVersion


ModuleVersionKey:
1. isPackage (byte)
2. digest (32 bytes)


ModuleVersion:
1. moduleSize (long)
2. localImportNames size (int)
3. For each import name,
   1. UTF8 import name
4. entryPoints size (int)
5. For each entry point,
   1. UTF8 entry point name
6. compilations size (int)
7. For each compilation,
   1. ModuleCompilationKey
   2. ModuleCompilation
8. moduleHeaderRecordNumber (long)
9. stacksRecordNumber (long)


ModuleCompilationKey:
1. predecessorCompilationTimes length (int)
2. For each predecessor compilation time,
   1. predecessor compilation time (long)


ModuleCompilation:
1. compilationTime (long)
2. recordNumber (long)
3. recordNumberOfBlockPhrases (long)
4. recordNumberOfManifestEntries (long)
5. recordNumberOfStyling (long)
-----------------------------------------------------------



StylingRecord
1. #styleNames
2. For each styleName,
   1. UTF8 styleName
3. #spans
4. For each span,
   1. styleNumber (compressed int, 0=no style)
   2. length (compressed int), measured in UTF-16 codepoints.
5. #declarations
6. For each declaration,
   1. delta (compressed, in UTF-16 codepoints) from end of previous declaration
   2. length (compressed, in UTF-16 codepoints)
   3. Optional 0 for special treatment (note: #usages cannot be zero)
   4. #usages, compressed
   5. For each usage,
   
      If normal,
      1. delta from end of previous declaration or usage (compressed, UTF-16).
         Size is assumed to be same as declaration in this case.

      If special treatment,
      1. position of start of usage in UTF-16 codepoints.
         Absolute for first usage of a declaration, otherwise relative to
         previous usage's end.
      2. size of usage token in UTF-16 codepoints.
