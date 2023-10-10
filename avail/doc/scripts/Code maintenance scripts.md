# Code maintenance
A development environment can't always support everything for code hygiene, so
any handy scripts and techniques can be listed here.

###File name agreement in file comments

From the base directory, this command looks at each Kotlin and Avail file, and
verifies that a line like ` * ThisFile.kt`, but with the actual filename, occurs
somewhere in it (expected to be on the second line of the file), and if not,
outputs the name of that file. This is a very common problem, as refactoring
does not update file comments. Note that it also identifies Kotlin files that
are entirely missing a file header comment.


```
find . -type f \( -name '*.kt' -or -name '*.avail' \) -print0 | xargs -0 -n 1 -I % -S 10000 bash -c 'b=$(basename "%"); grep -L " [\*] ${b}" "%"'
```

If this encounters problems (which it doesn't on Mac, as of 2022-03-06), adding
a `-x` after `bash` causes each of the shell invocations to print the grep
command being invoked.  That's how the need for `-S 10000` was detected – grep
was getting a literal `%` due to the command line exceeding `xargs`' default
minuscule 256 character limit... and it has the insane behavior to silently stop
substituting when that limit is reached.


###(Add other code hygiene scripts here...) 
