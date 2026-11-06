# `AbbreviateUtils.java`

Shrinks a SQL identifier to a dialect length budget while keeping it readable and collision-resistant. Exports `abbreviate(String text, Integer maxLength, String separator)`.

Splits camel-case and `_` into words, lower-cases, joins with `separator`, then deletes characters by English letter frequency — vowels `e a o i u` first, then consonants `t n s r h d l c m f y w g p b v k x q j z`, always from the rightmost word backwards and never the first character of a word. Throws `IllegalArgumentException` when `maxLength < 1`.

Output is deterministic for a given input, and every generated table/column name depends on it — changing the frequency tables renames existing schemas. Injected into the Epsilon context under key `AbbreviateUtils`.