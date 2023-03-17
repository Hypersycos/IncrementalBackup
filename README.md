# Incremental Backup
Incremental Backup provides a modular engine which can be used to store specialized incremental backups of files. A very naive binary handler is included, as is a specialized minecraft .MCA file handler. In my usage the incremental backup never used more than 2x the storage space than the original world file.

# Usage
## SwitchingIncrementalBackup
The SwitchingIncrementalBackup class is the heart of the engine. An instance of the class represents an ongoing backup from a directory to a backup directory. FileHandlers must be registered using the register method. This connects an instance of ITypeHandler to a given file extension (the . should not be included in the call). A set of ignored paths can also be provided in the constructor. If a filetype is not registered, the class will default to the naive binary chunk comparison.

The performIncrementalBackup is the method that should be used most of the time. performFullBackup will create a new directory and loses all previous differences. This will improve performance, but obviously requires much more space to be used.
## ITypeHandler
This interface defines how to combine and compare files, and also includes a few helper methods such as bufferToTrimmedArray. Make sure any implementations follow the guidelines set in the docstrings for combine and getDifference
## CompressionScheme
This class allows different compression methods to be used for storing the differences, without needing the comparison interface to implement it.
