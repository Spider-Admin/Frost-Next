/*
 UpdateMD5.java / Frost-Next
 Copyright (C) 2015  "The Kitty@++U6QppAbIb1UBjFBRtcIBZg6NU"

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation; either version 2 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/

package updatemd5;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOError;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.FileVisitor;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

import com.twmacinta.util.MD5;

public class UpdateMD5
{
    private final String CONFIG_XML = "UpdateMD5.xml";
    private final String LOCK_FILE = "UpdateMD5.lock";
    private FileLock lock = null;
    private File lockFile = null;
    private FileChannel lockChannel = null;

    private String nodeToString(
            final Node aNode)
    {
        if( aNode != null ) {
            try {
                final StringWriter buf = new StringWriter();
                final Transformer xform = TransformerFactory.newInstance().newTransformer();
                xform.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                xform.transform(new DOMSource(aNode), new StreamResult(buf));
                return buf.toString();
            } catch( final Exception ex ) {}
        }
        return null;
    }

    private boolean parseConfig(
            final File xmlFile,
            final Map<String,String> options,
            final SortedSet<String> fileExtensions,
            final Set<Path> includePaths,
            final Set<Path> excludePaths)
    {
        if( !xmlFile.isFile() || xmlFile.length() == 0 ) {
            System.out.println("Fatal Error: "+CONFIG_XML+" is missing or empty.");
            return false;
        }
        try {
            final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            final Document doc = dBuilder.parse(xmlFile);

            // join all disjointed strings in the DOM
            doc.getDocumentElement().normalize();

            final String rootNodeName = doc.getDocumentElement().getNodeName();
            if( !rootNodeName.equals("UpdateMD5") )
                return false;

            NodeList nList, nSubList;
            Node nNode, nSubNode;
            Element eElement, eSubElement;

            // Read all <options> tags (there should only be one)
            nList = doc.getElementsByTagName("Options");
            for( int i=0; i<nList.getLength(); ++i ) {
                nNode = nList.item(i);
                if( nNode.getNodeType() == Node.ELEMENT_NODE ) {
                    eElement = (Element) nNode;
                    nSubList = eElement.getElementsByTagName("option");
                    for( int x=0; x<nSubList.getLength(); ++x ) {
                        nSubNode = nSubList.item(x);
                        if( nSubNode.getNodeType() == Node.ELEMENT_NODE ) {
                            eSubElement = (Element) nSubNode;

                            final String optionName = eSubElement.getAttribute("name");
                            final String optionValue = eSubElement.getAttribute("value");
                            if( optionName == null || optionName.isEmpty() || optionValue == null || optionValue.isEmpty() ) {
                                System.out.println("Invalid option, must specify both name and value:");
                                System.out.println(nodeToString(nSubNode));
                                return false;
                            }

                            options.put(optionName, optionValue); // overwrites clashes
                        }
                    }
                }
            }

            // Read all <FileExtensionPatterns> tags (there should only be one)
            nList = doc.getElementsByTagName("FileExtensionPatterns");
            for( int i=0; i<nList.getLength(); ++i ) {
                nNode = nList.item(i);
                if( nNode.getNodeType() == Node.ELEMENT_NODE ) {
                    eElement = (Element) nNode;
                    nSubList = eElement.getElementsByTagName("pattern");
                    for( int x=0; x<nSubList.getLength(); ++x ) {
                        nSubNode = nSubList.item(x);
                        if( nSubNode.getNodeType() == Node.ELEMENT_NODE ) {
                            eSubElement = (Element) nSubNode;

                            final String patternName = eSubElement.getAttribute("name");
                            final String patternValue = eSubElement.getAttribute("value");
                            if( patternName == null || patternName.isEmpty() || patternValue == null || patternValue.isEmpty() ) {
                                System.out.println("Invalid file extension pattern, must specify both name and value:");
                                System.out.println(nodeToString(nSubNode));
                                return false;
                            }

                            // convert the extensions into a sorted, de-duplicated format
                            final String[] extargs = patternValue.split(";");
                            for( String ext : extargs ) {
                                ext = ext.trim().toLowerCase();
                                if( ext.isEmpty() )
                                    continue;
                                fileExtensions.add(ext);
                            }
                        }
                    }
                }
            }

            // Read all <Paths> tags (there should only be one)
            nList = doc.getElementsByTagName("Paths");
            for( int i=0; i<nList.getLength(); ++i ) {
                nNode = nList.item(i);
                if( nNode.getNodeType() == Node.ELEMENT_NODE ) {
                    eElement = (Element) nNode;
                    nSubList = eElement.getElementsByTagName("path");
                    for( int x=0; x<nSubList.getLength(); ++x ) {
                        nSubNode = nSubList.item(x);
                        if( nSubNode.getNodeType() == Node.ELEMENT_NODE ) {
                            eSubElement = (Element) nSubNode;

                            final String pathType = eSubElement.getAttribute("type");
                            final String pathValue = eSubElement.getAttribute("value");
                            if( pathType == null || pathType.isEmpty() || pathValue == null || pathValue.isEmpty() ) {
                                System.out.println("Invalid path, must specify both type and value:");
                                System.out.println(nodeToString(nSubNode));
                                return false;
                            }

                            if( !pathType.equals("include") && !pathType.equals("exclude") ) {
                                System.out.println("Invalid path type, must be either 'include' or 'exclude'.");
                                System.out.println(nodeToString(nSubNode));
                                return false;
                            }

                            final Path diskPath = resolveRealPath(pathValue);
                            if( diskPath != null ) {
                                if( pathType.equals("include") )
                                    includePaths.add(diskPath);
                                else if( pathType.equals("exclude") )
                                    excludePaths.add(diskPath);
                            } else {
                                System.out.println("Invalid filesystem path '"+pathValue+"'.");
                                System.out.println(nodeToString(nSubNode));
                                return false;
                            }
                        }
                    }
                }
            }

            // do basic first-line defense verification of all provided user-options.
            // we don't need to check if they're empty, since that's already taken care of above.
            if( includePaths.size() == 0 ) {
                System.out.println("Your "+CONFIG_XML+" file must specify at least one Path to include. Open "+CONFIG_XML+" in a text editor and look in the <Paths> section at the bottom for instructions.");
                return false;
            }
            if( fileExtensions.size() == 0 ) {
                System.out.println("Your "+CONFIG_XML+" file doesn't specify any file extensions to include.");
                return false;
            }
            if( options.get("OutputFile") == null ) {
                System.out.println("Your "+CONFIG_XML+" file must contain the OutputFile option.");
                return false;
            }
            if( options.get("BackupFile") == null ) {
                System.out.println("Your "+CONFIG_XML+" file must contain the BackupFile option.");
                return false;
            }
            if( options.get("MultiBackups") == null || ( !options.get("MultiBackups").equals("true") && !options.get("MultiBackups").equals("false") ) ) {
                System.out.println("Your "+CONFIG_XML+" file must contain the MultiBackups option, and its value must be either 'true' or 'false'.");
                return false;
            }
            if( options.get("PruneMissingFiles") == null || ( !options.get("PruneMissingFiles").equals("true") && !options.get("PruneMissingFiles").equals("false") ) ) {
                System.out.println("Your "+CONFIG_XML+" file must contain the PruneMissingFiles option, and its value must be either 'true' or 'false'.");
                return false;
            }
            if( options.get("PruneExcludedFiles") == null || ( !options.get("PruneExcludedFiles").equals("true") && !options.get("PruneExcludedFiles").equals("false") ) ) {
                System.out.println("Your "+CONFIG_XML+" file must contain the PruneExcludedFiles option, and its value must be either 'true' or 'false'.");
                return false;
            }
            double minFileSizeMiB = -1.0d;
            if( options.get("MinimumFileSizeMiB") != null ) {
                try {
                    minFileSizeMiB = Double.parseDouble(options.get("MinimumFileSizeMiB"));
                } catch( final Exception ex ) {}
            }
            if( minFileSizeMiB < 0 ) {
                System.out.println("Your "+CONFIG_XML+" file must contain the MinimumFileSizeMiB option, and its value must be a valid number (0 or higher).");
                return false;
            }
            if( options.get("Verbose") == null || ( !options.get("Verbose").equals("true") && !options.get("Verbose").equals("false") ) ) {
                System.out.println("Your "+CONFIG_XML+" file must contain the Verbose option, and its value must be either 'true' or 'false'.");
                return false;
            }

            return true;
        } catch( final Exception ex ) {
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * Constructor, called by UpdateMD5Launcher.java via reflection, if the user passed
     * the Java version check. This constructor acts identically to a regular
     * "public void main()" entry point.
     * @param {String[]} args - the command line arguments
     */
    public UpdateMD5(
            final String[] args)
    {
        System.out.println("[ Frost-Next MD5 Hash Utility ]\n");

        MD5.initNativeLibrary(); // enable native C library for all hashing; falls back to Java if lib is missing

        final Map<String,String> options = new HashMap<String,String>();
        final SortedSet<String> fileExtensions = new TreeSet<String>();
        final Set<Path> includePaths = new LinkedHashSet<Path>();
        final Set<Path> excludePaths = new LinkedHashSet<Path>();

        final File configXmlFile = new File(CONFIG_XML);
        if( !parseConfig(configXmlFile, options, fileExtensions, includePaths, excludePaths) ) {
            System.out.println("Your "+CONFIG_XML+" file is invalid.");
            System.exit(1);
        }

        long minFileSizeBytes = 0L;
        try {
            final double minFileSizeMiB = Double.parseDouble(options.get("MinimumFileSizeMiB"));
            minFileSizeBytes = Math.round(minFileSizeMiB * 1024.0d * 1024.0d);
        } catch( final Exception ex ) {}
        if( minFileSizeBytes <= 0L )
            minFileSizeBytes = 1L; // no smaller than 1 byte

        StringBuilder sb = new StringBuilder();
        sb.append("Configuration:\n")
            .append("- Output File: [").append(options.get("OutputFile")).append("]\n")
            .append("- Backup File: [").append(options.get("BackupFile")).append("]\n")
            .append("- Multiple Backups: [").append(options.get("MultiBackups")).append("]\n")
            .append("- Prune Missing Files: [").append(options.get("PruneMissingFiles")).append("]\n")
            .append("- Prune Excluded Files: [").append(options.get("PruneExcludedFiles")).append("]\n")
            .append("- Minimum File Size (Bytes): [").append(minFileSizeBytes).append("]\n")
            .append("- File Extensions: ").append(java.util.Arrays.toString(fileExtensions.toArray())).append("\n")
            .append("- Included Paths:\n");
        for( final Path inclPath : includePaths )
            sb.append("  * [").append(inclPath).append("]\n");
        sb.append("- Excluded Paths:\n");
        if( excludePaths.size() == 0 )
            sb.append("  * None.\n");
        for( final Path exclPath : excludePaths )
            sb.append("  * [").append(exclPath).append("]\n");
        System.out.println(sb.toString());
        sb = null;

        boolean hasValidPaths = false;
        for( final Path inclPath : includePaths ) {
            if( inclPath.toFile().isDirectory() ) {
                hasValidPaths = true;
                break;
            }
        }
        if( !hasValidPaths ) {
            System.out.println("Fatal Error: All of your included paths are missing. You need at least 1 valid path. Check your configuration.");
            System.exit(1);
        }

        final FileIndex fileIndex = new FileIndex(); // the path & md5 index
        final File outputFile = new File(options.get("OutputFile"));

        // before we do anything, let's set up an exclusive lockfile to prevent
        // multiple instances from running simultaneously, as well as a shutdown
        // hook so that we write our current progress if we're told to exit in
        // the middle of a hashing job (such as if the user hits Ctrl+C).
        try {
            lockFile = new File(LOCK_FILE);
            lockChannel = new RandomAccessFile(lockFile, "rw").getChannel();
            try {
                lock = lockChannel.tryLock();
            } catch( final OverlappingFileLockException e ) {
                // means we already have a lock on this file within this JVM or one of our threads
            }
        } catch( final Exception ex ) {} // ignore errors
        if( lock == null ) {
            System.out.println("Lock file found. This indicates that another UpdateMD5 instance is already running. Running multiple instances concurrently will cause data loss.\n\nIf you are REALLY SURE that UpdateMD5 is not currently running, then delete the lockfile: ["+LOCK_FILE+"].");
            System.exit(1);
        }
        final AtomicBoolean writeHashesAtShutdown = new AtomicBoolean(false);
        Runtime.getRuntime().addShutdownHook(new Thread("ShutdownHook") {
            @Override
            public void run() {
                if( writeHashesAtShutdown.get() ) {
                    // the user forced the program to end abruptly; but the
                    // fact that the shutdown hook is running means that we've
                    // at least loaded *all* entries from the old file, and possibly
                    // hashed new entries, so let's write everything to disk!
                    // NOTE: in some rare cases, the operating system may only
                    // give us a limited amount of time to finish our work before
                    // it forcibly terminates us. but in MOST cases, the USER
                    // initiated the shutdown with Ctrl+C which is a "soft quit"
                    // signal which means that we literally have infinite time.
                    // also, if the JVM is sent KILL, the shutdown won't run.
                    System.out.println("\nUser requested abrupt program termination, saving all current work...\n");
                    writeHashes(outputFile, fileIndex, /*exitOnError=*/false);
                }
                try {
                    if( lock != null )
                        lock.release();
                    if( lockChannel != null )
                        lockChannel.close();
                    lockFile.delete();
                } catch( final Exception ex ) {}
            }
        });

        // we have a lock! let's begin!
        if( outputFile.isFile() && outputFile.length() > 0 ) {
            String backupFilePath = options.get("BackupFile");
            if( !backupFilePath.equalsIgnoreCase("none") ) {
                if( options.get("MultiBackups").equals("true") ) {
                    final String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    backupFilePath = backupFilePath + "." + timeStamp;
                }
                final File backupFile = new File(backupFilePath);
                System.out.println("Backing up '"+outputFile+"' to '"+backupFile+"'...\n");
                try {
                    Files.copy(outputFile.toPath(), backupFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                } catch( final Exception ex ) {
                    System.out.println("Fatal Error: Unable to create backup copy...");
                    System.exit(1);
                }
            }

            // since there's a previous, non-empty hash file, we must begin
            // by populating the index with all old hashes.
            System.out.println("Loading previous hashes from '"+options.get("OutputFile")+"'...\n");
            try( final BufferedReader br = new BufferedReader(new InputStreamReader(
                            new FileInputStream(outputFile), "UTF-8")) )
            {
                // we will only import "X:"-prefixed paths if we're on Windows,
                // and only "/"-prefixed paths if we're on Linux/Mac.
                final String validPathPrefixRegex = ( getOSName().equals("Windows") ? "^[a-zA-Z]:" : "^/" );
                final Pattern validPathPrefixPattern = Pattern.compile(validPathPrefixRegex);
                final Matcher validPathPrefixMatcher = validPathPrefixPattern.matcher("");

                boolean pruneExcludedFiles = options.get("PruneExcludedFiles").equals("true");
                long pruneExcludedCount = 0L;
                String line;
                while( (line = br.readLine()) != null ) {
                    // parse the line of text, expecting md5sum format:
                    // "[md5][space][binaryflag(" "=ascii or "*"=binary)][filepath]"
                    // the minimum length for valid line is 35 (32 md5 hex, 1 space, 1 flag, 1 filename letter).
                    // the 33rd character (32nd index) must be whitespace (the space separator),
                    // and the 34th character (33rd index) must be space or an asterisk.
                    if( line != null && line.length() >= 35 && Character.isWhitespace(line.charAt(32))
                            && ( Character.isWhitespace(line.charAt(33)) || line.charAt(33) == '*' ) ) {
                        final String md5Hex = line.substring(0, 32);
                        final String filePath = line.substring(34).trim();
                        if( !filePath.isEmpty() && validPathPrefixMatcher.reset(filePath).find() ) {
                            final Path diskPath = resolveRealPath(filePath);
                            if( diskPath != null ) { // legal path for this filesystem (might not exist, though)
                                if( pruneExcludedFiles ) {
                                    // user wants to prune files that either don't belong
                                    // under their included paths, or that sit under
                                    // excluded paths, so let's first check the path.
                                    if( !isPathIncluded(diskPath, includePaths, excludePaths) ) {
                                        pruneExcludedCount++;
                                        continue; // skip this line
                                    }
                                }
                                // this might be a valid line, so let's try adding it...
                                // it will only be added if it's actually a valid hash.
                                // known paths will be updated to their latest-seen hash.
                                // multiple files with the same hash will be grouped.
                                // duplicate path entries for moved files are removed.
                                //
                                // NOTE: we add the file using its *real* on-disk
                                // case; so a Windows user who previously hashed "J:\foo"
                                // and then renamed the folder to Foo" will have their
                                // filenames imported as "J:\Foo", which ensures that
                                // they aren't treated as duplicate files with diff names.
                                // however, if the file is missing on-disk, we use whatever
                                // case was stored in files.md5, which is fine since the
                                // various path-comparison functions are case-insensitive
                                // on Windows.
                                try {
                                    fileIndex.addWithMoveDetection(md5Hex, diskPath.toString(), /*verbose=*/false);
                                } catch( final Exception ex ) {}
                            }
                        }
                    }
                }
                if( pruneExcludedFiles )
                    System.out.println("Pruned "+pruneExcludedCount+" excluded files from your previous index.\n");
            } catch( final Exception ex ) {} // ignore file-reader errors
            System.out.println("Loaded "+fileIndex.getUniqueFileCount()+" unique file paths, with "+fileIndex.getUniqueMD5Count()+" unique MD5 hashes.\n");
        }

        System.out.println("Let's start hashing!\n");
        writeHashesAtShutdown.set(true); // tell shutdown thread to write all current hashes if the job is aborted
        final boolean verbose = options.get("Verbose").equals("true");
        for( final Path inclPath : includePaths ) {
            indexPath(inclPath, fileIndex, includePaths, excludePaths, fileExtensions, minFileSizeBytes, verbose);
        }

        if( options.get("PruneMissingFiles").equals("true") ) {
            System.out.println("Pruning any missing files from your index...\n");
            List<String> removePaths = null;
            for( Map.Entry<String,MD5MultiFileHash> entry : fileIndex.filePathSet() ) {
                final String filePath = entry.getKey();
                if( filePath == null )
                    continue;
                try {
                    if( !(new File(filePath).exists()) ) {
                        System.out.println("* Pruned missing file ["+filePath+"].");
                        if( removePaths == null )
                            removePaths = new ArrayList<String>(100);
                        removePaths.add(filePath);
                    }
                } catch( final Exception ex ) {} // skip files with permissions errors
            }
            if( removePaths != null ) {
                // actual removal is done here as separate step, otherwise
                // we'd have invalidated the "filePathSet()" iterator.
                for( final String deleteMe : removePaths )
                    fileIndex.remove(deleteMe);
                System.out.println(); // empty line after list of deletions
            }
        }

        // all done; just output! this writes the filepaths in a sorted, case-insensitive order
        // NOTE: if the user hits Ctrl+C while it's writing, the shutdown hook will run and
        // will start a new, fresh write, which means the user *cannot* screw up their .md5
        // by trying to abort during the final writing stage. ;-)
        writeHashes(outputFile, fileIndex, /*exitOnError=*/true);
        writeHashesAtShutdown.set(false); // tell shutdown cleanup thread that we don't need its help
        System.exit(0);
    }

    private void writeHashes(
            final File outputFile,
            final FileIndex fileIndex,
            final boolean exitOnError)
    {
        if( outputFile == null || fileIndex == null )
            return;

        System.out.println("Writing hashes to '"+outputFile+"': "+fileIndex.getUniqueFileCount()+" unique file paths, with "+fileIndex.getUniqueMD5Count()+" unique MD5 hashes.\n");
        try {
            if( outputFile.isFile() )
                outputFile.delete();
            try( final BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                            new FileOutputStream(outputFile), "UTF-8")) )
            {
                for( Map.Entry<String,MD5MultiFileHash> entry : fileIndex.filePathSet() ) {
                    final String filePath = entry.getKey();
                    final MD5MultiFileHash fileHash = entry.getValue();
                    if( filePath == null || fileHash == null )
                        continue;
                    // write in md5sum format (since it's the "standard" format for .md5 files)
                    out.write(fileHash.getMD5String());
                    out.write(" *"); // separator and type (* for binary)
                    out.write(filePath);
                    out.write("\n"); // must be UNIX newline, otherwise md5sum CANNOT parse it
                }
            }
            System.out.println("All done! Now go out there and kick some ass!");
        } catch( final Exception ex ) {
            System.out.println("Fatal Error: No permissions to overwrite '"+outputFile+"', or other write error...");
            if( ex.getMessage() != null )
                System.out.println("Exact Error: "+ex.getMessage());
            if( exitOnError )
                System.exit(1);
        }
    }

    /**
     * Converts a string to a normalized, absolute on-disk path with the correct
     * case (same as the files on disk). If the file doesn't exist, it does its
     * best to at least create an absolute path representation.
     *
     * @return  a clean Path, or null if the path was illegal for the current
     *     filesystem (such as containing illegal characters). in case of a null,
     *     you may want to preserve the user's original string as-is instead.
     */
    private Path resolveRealPath(
            final String inputString)
    {
        try {
            // convert string to Path object *without* checking if it exists or resolving
            // any symlinks, while also converting relative paths like "downloads/" to
            // absolute, and resolving (normalizing) "." and ".." path components, so that
            // all paths will be possible to compare to each other later.
            Path diskPath = Paths.get(inputString).toAbsolutePath().normalize();
            // now, if the file exists, we'll further try to resolve the user-provided path into its *real* path.
            // for example, on Windows, a path of "j:\foo\bar.txt" could be resolved to "J:\Foo\bar.txt",
            // meaning an uppercase drive-letter, and the correct on-disk case of any files/folders.
            // this is extremely important, so that Windows users can freely rename their folders
            // and change their case, without them being treated as completely different files.
            // we don't follow symlinks, which means that on Linux, symlinks in the path remain intact.
            try {
                final Path realDiskPath = diskPath.toRealPath(LinkOption.NOFOLLOW_LINKS);
                diskPath = realDiskPath;
            } catch( final Exception e ) {} // file didn't exist, so just use their path as-is
            return diskPath;
        } catch( final InvalidPathException | IOError | SecurityException e ) {
            // the path string was invalid (illegal characters or other filesystem-specific reasons)...
            return null;
        }
    }

    /**
     * Performs a memory-efficient path traversal, which doesn't
     * load any filenames into memory until we actually visit them.
     */
    private boolean indexPath(
            final Path startPath,
            final FileIndex fileIndex,
            final Set<Path> includePaths,
            final Set<Path> excludePaths,
            final SortedSet<String> fileExtensions,
            long minFileSizeBytes,
            final boolean verbose)
    {
        final File startFile = startPath.toFile();
        final int testResult = checkFileAndPathCriteria(startFile, startPath, includePaths, excludePaths, fileExtensions, minFileSizeBytes, /*isKnownFile=*/false);

        System.out.println("### Path: ["+startPath+"]");
        switch( testResult ) {
            case PATH_EXCLUDED:
                System.out.println("Result: Path is excluded by one of your exclude-filters, skipping...\n\n");
                return false;
            case FILE_OK_FILE:
                System.out.println("Result: Path is a file. Indexing...\n");
                addFileToIndex(startFile, fileIndex, verbose);
                System.out.println(""); // empty line after single file
                return true;
            case FILE_OK_DIRECTORY:
                System.out.println("Result: Path is a directory. Indexing...\n");
                try {
                    // follows symlinks and tracks visited dirs to avoid cycles; no limit to depth search
                    Files.walkFileTree(startPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new FileVisitor<Path>() {
                        /**
                         * Invoked for a directory before entries in the directory are visited.
                         */
                        @Override
                        public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                            if( dir != null ) {
                                if( !isPathIncluded(dir, includePaths, excludePaths) ) {
                                    if( verbose )
                                        System.out.println("* Directory ["+dir+"] is excluded by one of your exclude-filters, skipping...");
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                                if( verbose )
                                    System.out.println("* Entering directory ["+dir+"]...");
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        /**
                        * Invoked for a file in a directory.
                        */
                        @Override
                        public FileVisitResult visitFile(final Path filePath, final BasicFileAttributes attrs) throws IOException {
                            if( filePath != null ) {
                                final File fileFile = filePath.toFile();
                                final int fileTestResult = checkFileAndPathCriteria(fileFile, filePath, includePaths, excludePaths, fileExtensions, minFileSizeBytes, /*isKnownFile=*/true);
                                switch( fileTestResult ) {
                                    case PATH_EXCLUDED:
                                        if( verbose )
                                            System.out.println("* File ["+filePath+"] is excluded by one of your exclude-filters, skipping...");
                                        break;
                                    case FILE_OK_FILE:
                                        addFileToIndex(fileFile, fileIndex, verbose);
                                        break;
                                    case FILE_MISSING: // can only happen if the check's File.length() operation fails
                                        if( verbose )
                                            System.out.println("* Error while reading ["+filePath+"], skipping...");
                                        break;
                                    case FILE_EXT_EXCLUDED:
                                        if( verbose )
                                            System.out.println("* File ["+filePath+"] has an excluded extension, skipping...");
                                        break;
                                    case FILE_TOO_SMALL:
                                        if( verbose )
                                            System.out.println("* File ["+filePath+"] is too small, skipping...");
                                        break;
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        /**
                        * Invoked for a file that could not be visited.
                        * This method is invoked if the file's attributes could
                        * not be read, the file is a directory that could not be
                        * opened, and other reasons.
                        */
                        @Override
                        public FileVisitResult visitFileFailed(final Path file, final IOException ex) throws IOException {
                            if( file != null )
                                System.out.println("* Error while reading ["+file+"], skipping...");

                            // let's ignore the problem and continue with other files
                            return FileVisitResult.CONTINUE;
                            //throw new IOError(ex);
                        }

                        /**
                        * Invoked for a directory after entries in the directory,
                        * and all of their descendants, have been visited. This
                        * method is also invoked when iteration of the directory
                        * completes prematurely (by a visitFile method returning
                        * SKIP_SIBLINGS, or an I/O error when iterating over dir).
                        */
                        @Override
                        public FileVisitResult postVisitDirectory(final Path dir, final IOException ex) throws IOException {
                            // let's ignore the problem and continue with other files
                            return FileVisitResult.CONTINUE;
                            //if( ex != null )
                            //    throw new IOError(ex);
                        }
                    });
                    System.out.println(""); // empty line after directory job
                } catch( final Exception ex ) {
                    System.out.println("Result: Error while processing directory, skipping...\n");
                }
                return true;
            case FILE_MISSING:
                System.out.println("Result: Path does not exist, skipping...\n\n");
                return false;
            case FILE_EXT_EXCLUDED:
                System.out.println("Result: File extension is excluded, skipping...\n\n");
                return false;
            case FILE_TOO_SMALL:
                System.out.println("Result: File is too small, skipping...\n\n");
                return false;
        }

        return true;
    }

    /**
     * Hashes and adds/updates a file directly to the index. It's up to the caller
     * to guarantee that the file exists and is included in the user's include-filters.
     *
     * @return  true if the file was new/moved and thereby added to the index,
     *     false if the file was skipped (if invalid or already existed in index).
     */
    private boolean addFileToIndex(
            final File file,
            final FileIndex fileIndex,
            final boolean verbose)
    {
        if( file == null )
            return false;

        // skip file if path is known
        final MD5MultiFileHash oldHash = fileIndex.getByFilePath(file.toString());
        if( oldHash != null ) {
            if( verbose )
                System.out.println("* File ["+file+"] is already hashed, skipping...");
            return false;
        }

        try {
            // generate hash for unknown file
            System.out.println("* Hashing ["+file+"]...");
            final String hash = hashFile(file);
            if( hash == null ) {
                System.out.println("* Error while hashing ["+file+"], skipping...");
                return false;
            }

            // attempt to add the new hash to the index...
            // it will only be added if it's actually a valid hash.
            // known paths will be updated to their latest-seen hash.
            // multiple files with the same hash will be grouped.
            // duplicate path entries for moved files are removed.
            final MD5MultiFileHash newHash = fileIndex.addWithMoveDetection(hash, file.toString(), /*verbose=*/true);
            if( newHash == null ) {
                System.out.println("* Illegal hash '"+hash+"' generated for file ["+file+"], skipping...");
                return false;
            }

            return true;
        } catch( final Exception ex ) {
            return false;
        }
    }

    /**
     * Generates an MD5 from a given file. Result is a lowercase hex string.
     *
     * @return  null on failure, otherwise a 32-character hex string
     */
    private String hashFile(
            final File file)
    {
        /* The FastMD5 native library was arrived at after hours of testing:
         * Hashing 24 gigabytes of data from an SSD capable of 650MB/s reads:
         * - Using Java 7 Memory-Mapped file + MessageDigest: 4m32.373s real
         *   Trying lots of different memory mapping models, including
         *   loading the entire file via load(). Nothing made it faster.
         * - Using Java 6 File I/O + MessageDigest: 2m7.680s real, 1m31.376s user, 0m13.274s sys
         *   This was very dependent on the buffer-size. A buffer of 8192 bytes
         *   lead to a processing time of around 5 minutes, and a buffer of 2 MiB
         *   gave a much better ~2 minutes. But it was still slow. CPU was ~85%.
         *   The disk was being read at around 190MB/s. The high "user" CPU time above
         *   proves that it's being limited by CPU calculations inside Java.
         * - FastMD5's Java-only implementation: 1m51.624s real, 1m38.818s user, 0m10.702s sys
         *   Despite being completely written in Java, it beats the pants off
         *   Java's own MD5 hasher. Read speed was around 250MB/s, up to 380 sometimes.
         *   The limitation was yet again the CPU usage of running entirely in Java.
         * - OpenSSL's native MD5 feature: 1m13.564s real, 0m42.840s user, 0m11.544s sys
         *   Very fast, obviously. But we can't depend on that executable.
         * - FastMD5's native (written in C) helper: 1m15.362s real, 0m49.241s user, 0m10.741s sys
         *   Voila! Getting around 345-450MB/s read speed (hashing speed) consistently,
         *   and CPU usage is only around 80%. It equals the OpenSSL speeds!
         *   There's simply no reason to cripple the hasher by using any slower solutions.
         */
        try {
            final String hash = MD5.asHex(MD5.getHash(file));
            return hash;
        } catch( final Exception ex ) {} // ignore hashing errors
        return null;
    }

    /**
     * Checks if the Path and the real filesystem object matches the criteria.
     * Yes, you must provide both the File and the Path object pointing
     * to the exact same file. This is a small optimization, since the caller
     * itself needs both types of objects, so it's pointless for us to create
     * yet another File instance since the caller already has it.
     *
     * @param  isKnownFile  set to true if this file is known to exist and
     *     to be a file; in that case, we can skip some of the checks.
     */
    private static final int PATH_EXCLUDED = -1;
    private static final int FILE_OK_FILE = 0;
    private static final int FILE_OK_DIRECTORY = 1;
    private static final int FILE_MISSING = 2;
    private static final int FILE_EXT_EXCLUDED = 3;
    private static final int FILE_TOO_SMALL = 4;
    private int checkFileAndPathCriteria(
        final File testFile,
        final Path testPath,
        final Set<Path> includePaths,
        final Set<Path> excludePaths,
        final SortedSet<String> fileExtensions,
        long minFileSizeBytes,
        boolean isKnownFile)
    {
        try {
            if( !isPathIncluded(testPath, includePaths, excludePaths) )
                return PATH_EXCLUDED;

            if( !isKnownFile && !testFile.exists() )
                return FILE_MISSING;

            if( isKnownFile || testFile.isFile() ) {
                if( !validFileExt(testFile.getName(), fileExtensions) )
                    return FILE_EXT_EXCLUDED;
                if( testFile.length() < minFileSizeBytes )
                    return FILE_TOO_SMALL;
                return FILE_OK_FILE;
            }
            else
                return FILE_OK_DIRECTORY;
        } catch( final Exception ex ) {
            return FILE_MISSING; // one of the File operations failed, usually permissions-related
        }
    }

    /**
     * Checks if path belongs under an include-path,
     * and that it isn't under any of the exclude-paths.
     * This does not access the filesystem and doesn't resolve
     * any symlinks.
     * If the excluded (or included) patterns are files instead
     * of directories, they'll be correctly handled too (since
     * "/foo.txt".startsWith("/foo.txt") is true).
     * Furthermore, on Windows, this check is case-insensitive,
     * which means that "j:\somefile.avi" startsWith "J:\".
     */
    private boolean isPathIncluded(
            final Path testPath,
            final Set<Path> includePaths,
            final Set<Path> excludePaths)
    {
        for( final Path inclPath : includePaths ) {
            if( testPath.startsWith(inclPath) ) {
                for( final Path exclPath : excludePaths ) {
                    if( testPath.startsWith(exclPath) ) {
                        return false; // is under excluded path
                    }
                }
                return true; // valid: is under include and NOT any excludes
            }
        }
        return false; // neither in includes nor in excludes
    }

    /**
     * Checks if the file extension matches the valid extension filters.
     */
    private boolean validFileExt(
            final String fileName,
            final SortedSet<String> fileExtensions)
    {
            // now check if the current file's extension matches this filter
            // NOTE: this automatically lowercases the extension, and returns null if there is no ext
            final String fileExt = getFileExtension(fileName);
            if( fileExt == null )
                return false; // reject files without extension
            for( final String validExt : fileExtensions ) {
                // if this pattern uses the special "#" (any number) matching, then only try that pattern
                // NOTE: this is checked 1st to prevent something like "r##" from matching the literal "example.r##".
                if( validExt.indexOf('#') > -1 ) {
                    // the extension lengths must be equal, otherwise this pattern won't match
                    if( fileExt.length() != validExt.length() )
                        continue; // skip this filter since it didn't match

                    // now compare the file ext and the pattern character by character
                    boolean matchesPattern = true; // will be set to false at mismatch
                    char pc, fc; // pc = pattern char, fc = file char
                    for( int i=0; i<validExt.length(); ++i ) {
                        pc = validExt.charAt(i);
                        fc = fileExt.charAt(i);
                        if( pc == '#' ) {
                            // there's a # in the pattern, so the file must now have a number
                            // NOTE: we only accept the ASCII numbers 0-9, since file extensions
                            // never use unicode/arabic numbers. hex: 0 = 0x30, ..., 9 = 0x39.
                            if( fc < 0x30 || fc > 0x39 ) {
                                matchesPattern = false;
                                break;
                            }
                        } else {
                            // there's a regular character in the pattern, so the chars must be equal
                            if( fc != pc ) {
                                matchesPattern = false;
                                break;
                            }
                        }
                    }

                    // if the whole pattern matched, then accept this file
                    if( matchesPattern )
                        return true;
                }
                // otherwise; for plain extension patterns, just check the string
                else if( fileExt.equals(validExt) )
                    return true;
            }

            // file didn't match any valid extensions
            return false;
    }

    /**
     * Gives you the (lowercase) extension of the input file, or null if there isn't any extension.
     */
    private String getFileExtension(
            final String fileName)
    {
        if( fileName != null ) {
            final int pos = fileName.lastIndexOf('.');
            if( pos >= 0 ) {
                final int start = pos + 1; // skip the period
                if( start < fileName.length() ) { // avoid going out of bounds if the period is last
                    final String ext = fileName.substring(start).trim().toLowerCase();
                    if( ext != null && !ext.isEmpty() ) {
                        return ext;
                    }
                }
            }
        }
        return null;
    }

    private String getOSName()
    {
        // determine what operating system they're using
        String osName = System.getProperty("os.name").toLowerCase();
        if( osName.indexOf("windows") > -1 ) { osName = "Windows"; }
        else if( osName.indexOf("mac") > -1 ) { osName = "Mac"; }
        else { osName = "Linux"; } // actually Linux, Unix or Solaris, but we only support Linux
        return osName;
    }

    /**
     * Fast memory index of sorted filename paths and MD5s
     */
    private class FileIndex
    {
        // both of these indexes are unique (so only 1 unique filepath/MD5 per index),
        // but the MD5 index holds multiple file paths within the MD5MultiFileHash object.
        // the string objects used as both key and path-values re-use the same memory,
        // and both indexes contain references to the exact same MD5MultiFileHash objects.
        SortedMap<String,MD5MultiFileHash> filePathIndex;
        Map<MD5MultiFileHash,MD5MultiFileHash> md5HashIndex;

        // case-insensitive string comparator which sorts file paths in case-insensitive,
        // ascending order, but ensures that "equal" strings "ABC" and "abc" are resolved
        // via regular case-insensitive comparison, so that the Map contract is fulfilled.
        // the contract states that if the comparator thinks they're equal, the keys MUST
        // be equal; which obviously "ABC" and "abc" are NOT. but by doing a case-sensitive
        // comparison in case of "equality", we ensure that both are sorted as a group but
        // treated as unequal, thus fulfilling the contract.
        private final Comparator<String> FILEPATH_IGNORE_CASE_COMPARATOR = new Comparator<String>() {
            @Override
            public int compare(
                    final String s1,
                    final String s2)
            {
                int result = s1.compareToIgnoreCase(s2);
                if( result == 0 ) // possibly equal...
                    result = s1.compareTo(s2); // run case-sensitive to make sure
                return result;
            }
        };

        public FileIndex()
        {
            filePathIndex = new TreeMap<String,MD5MultiFileHash>(FILEPATH_IGNORE_CASE_COMPARATOR);
            md5HashIndex = new HashMap<MD5MultiFileHash,MD5MultiFileHash>();
        }

        /**
         * Adds an item by removing its old entry (if it exists) and then adding
         * the new entry. It will be merged in with other paths in case this
         * particular MD5 is already known.
         *
         * NOTE: It's recommended to use addWithMoveDetection() instaed.
         *
         * @return  the MD5 hash object in the index, if input was valid;
         *     otherwise returns null
         */
        public MD5MultiFileHash add(
                final String aHashStr,
                final String aFilePath)
        {
            MD5MultiFileHash newHash = stringToMD5(aHashStr);
            if( newHash == null )
                return null;

            remove(aFilePath); // remove old index/hash entries for this exact path

            final MD5MultiFileHash foundHash = md5HashIndex.get(newHash);
            if( foundHash != null )
                newHash = foundHash; // update existing MD5 object
            else
                md5HashIndex.put(newHash, newHash); // add new hash object to index
            newHash.addFilePath(aFilePath);
            filePathIndex.put(aFilePath, newHash);
            return newHash;
        }

        /**
         * Performs the same basic work as add(), but then it looks at the
         * final MD5 object. If it contains more than 1 file path, it checks
         * them all to look for obviously moved files, and deletes the old
         * paths via remove().
         *
         * Definition of a moved file:
         * - Same MD5 hash.
         * - Same basic filename (i.e. "foo.txt"), regardless of its path.
         * - The old file is missing.
         *
         * This extra checking is fast, since most hashes only have one
         * entry. It's also very important, to avoid bloating your database.
         *
         * @return  @see add
         */
        public MD5MultiFileHash addWithMoveDetection(
                final String aHashStr,
                final String aFilePath,
                final boolean verbose)
        {
            final MD5MultiFileHash newHash = add(aHashStr, aFilePath);
            if( newHash == null )
                return null;
            if( newHash.getFilePathCount() > 1 ) {
                final Path addedPath = Paths.get(aFilePath);
                final Path addedFileName = addedPath.getFileName();
                List<String> removePaths = null;
                for( final String p : newHash.getFilePaths() ) {
                    final Path oldPath = Paths.get(p);
                    if( oldPath.getFileName().equals(addedPath.getFileName()) ) {
                        if( oldPath.equals(addedPath) )
                            continue; // skip ourselves
                        if( !oldPath.toFile().exists() ) {
                            // alright, this is the same filename and hash,
                            // and its old path doesn't exist anymore.
                            // delete the old path from the index.
                            if( verbose )
                                System.out.println("* Detected moved file, updating reference ["+oldPath+"] --> ["+addedPath+"].");
                            if( removePaths == null )
                                removePaths = new ArrayList<String>(2);
                            removePaths.add(oldPath.toString());
                        }
                    }
                }
                if( removePaths != null ) {
                    // actual removal is done here as separate step, otherwise
                    // we'd have invalidated the "getFilePaths()" iterator.
                    for( final String deleteMe : removePaths )
                        remove(deleteMe);
                }
            }
            return newHash;
        }

        /**
         * Removes a known filepath from its MD5 object, and removes
         * the MD5 object from all indexes if that was its last path.
         */
        public void remove(
                final String aFilePath)
        {
            if( aFilePath == null )
                return;
            final MD5MultiFileHash oldHash = filePathIndex.get(aFilePath);
            if( oldHash != null ) {
                filePathIndex.remove(aFilePath);
                oldHash.removeFilePath(aFilePath);
                if( oldHash.getFilePathCount() == 0 )
                    md5HashIndex.remove(oldHash);
            }
        }

        /**
         * Returns an iterable, ascending (case sensitive) view of every file
         * in this index. You are not allowed to modify the entries! They are only
         * supposed to be touched by add() and remove(), to maintain proper
         * synchronization between the two internal indexes!
         *
         * To iterate:
         * for( Map.Entry<String,MD5MultiFileHash> entry : filePathSet() ) {
         *     final String filePath = entry.getKey();
         *     final MD5MultiFileHash md5Hash = entry.getValue();
         * }
         *
         * WARNING: DO NOT TRY TO CALL remove() ON A FILEPATH WHILE YOU'RE ITERATING
         * OVER THE COLLECTION. YOU WILL CAUSE A MODIFICATION OF THE INDEX, WHICH
         * INVALIDATES YOUR ITERATOR AND THROWS A CONCURRENTMODIFICATIONEXCEPTION!
         * THE CORRECT WAY TO ITERATE AND DELETE ENTRIES IS TO BUILD A SEPARATE
         * LIST OF ALL THE FILEPATH STRINGS YOU WANT TO REMOVE, AND REMOVE THEM
         * AFTERWARDS. DON'T WORRY ABOUT MEMORY; YOUR STRINGS WILL BE REFERENCES
         * TO WHAT'S ALREADY IN THE INDEX.
         */
        public Set<Map.Entry<String,MD5MultiFileHash>> filePathSet()
        {
            return filePathIndex.entrySet();
        }

        /**
         * The number of unique file paths in the index.
         */
        public int getUniqueFileCount()
        {
            return filePathIndex.size();
        }

        /**
         * The number of unique hashes in the index.
         */
        public int getUniqueMD5Count()
        {
            return md5HashIndex.size();
        }

        /**
         * @return  null if no match
         */
        public MD5MultiFileHash getByFilePath(
                final String aFilePath)
        {
            return filePathIndex.get(aFilePath);
        }

        /**
         * Queries "by example"; returned object is *not* the same as the input object.
         * @return  null if no match
         */
        public MD5MultiFileHash getByMD5(
                final MD5MultiFileHash aHashObj)
        {
            return md5HashIndex.get(aHashObj);
        }

        /**
         * @return  null if invalid MD5
         */
        public MD5MultiFileHash stringToMD5(
                final String aHashStr)
        {
            try {
                final MD5MultiFileHash newHash = new MD5MultiFileHash(aHashStr);
                return newHash;
            } catch( final IllegalArgumentException ex ) {
                return null; // invalid MD5 string
            }
        }
    }

    /**
     * This is a copy of Frost-Next's source/frost/fileTransfer/download/HashBlocklistTypes/MD5FileHash.java.
     *
     * It allows us to represent MD5 hashes in memory in a very efficient manner.
     * All comments from the real file have been stripped, and the Perst dependency
     * has been removed (which saves some memory).
     * Furthermore, it has been changed to hold multiple paths per hash, which
     * uses a bit more memory but allows us to store multiple files with the same MD5,
     * and the extra memory is comparable to what we saved by removing Perst.
     */
    private class MD5MultiFileHash
    {
        private long fTopMD5Bits;
        private long fBottomMD5Bits;
        private List<String> fFilePaths;

        public MD5MultiFileHash(
                final String aHashStr)
            throws IllegalArgumentException
        {
            if( aHashStr == null || aHashStr.length() != 32 )
                throw new IllegalArgumentException("Invalid hash provided; must be 32-character MD5 hex string.");

            try {
                fTopMD5Bits = Long.parseUnsignedLong(aHashStr.substring(0, 16), 16);
                fBottomMD5Bits = Long.parseUnsignedLong(aHashStr.substring(16), 16);
            } catch( final Exception ex ) {
                throw new IllegalArgumentException("Invalid hash provided; not a legal hex string.");
            }

            fFilePaths = new LinkedList<String>();
        }

        public MD5MultiFileHash(
                final long aTopMD5Bits,
                final long aBottomMD5Bits)
        {
            fTopMD5Bits = aTopMD5Bits;
            fBottomMD5Bits = aBottomMD5Bits;
            fFilePaths = new LinkedList<String>();
        }

        public long getTopMD5Bits()
        {
            return fTopMD5Bits;
        }

        public long getBottomMD5Bits()
        {
            return fBottomMD5Bits;
        }

        public void addFilePath(
                final String aFilePath)
        {
            if( !fFilePaths.contains(aFilePath) )
                fFilePaths.add(aFilePath);
        }

        public void removeFilePath(
                final String aFilePath)
        {
            fFilePaths.remove(aFilePath);
        }

        /**
         * WARNING: DO NOT MODIFY THE LIST WHILE ITERATING OVER IT, OR YOU WILL
         * GET A CONCURRENTMODIFICATIONEXCEPTION! USE THE ITERATOR INSTEAD
         * IF YOU WANT TO MODIFY THE LIST!
         */
        public List<String> getFilePaths()
        {
            return fFilePaths;
        }

        /**
         * The iterator supports the ListIterator.add() and ListIterator.remove()
         * functions, which allows you to safely remove items while iterating.
         */
        public ListIterator<String> getFilePathListIterator()
        {
            return fFilePaths.listIterator();
        }

        public int getFilePathCount()
        {
            return fFilePaths.size();
        }

        public String getMD5String()
        {
            final StringBuilder sb = new StringBuilder(32);

            final String topHex = Long.toUnsignedString(fTopMD5Bits, 16);
            if( topHex.length() < 16 )
                sb.append("0000000000000000".substring(topHex.length()));
            sb.append(topHex);

            final String bottomHex = Long.toUnsignedString(fBottomMD5Bits, 16);
            if( bottomHex.length() < 16 )
                sb.append("0000000000000000".substring(bottomHex.length()));
            sb.append(bottomHex);

            return sb.toString();
        }

        @Override
        public String toString()
        {
            return getMD5String();
        }

        @Override
        public int hashCode()
        {
            return (int)(fTopMD5Bits & 0xffffffffL);
        }

        @Override
        public boolean equals(
                final Object obj)
        {
            if( !(obj instanceof MD5MultiFileHash) )
                return false;
            if( obj == this )
                return true;

            final MD5MultiFileHash other = (MD5MultiFileHash) obj;
            return ( fTopMD5Bits == other.fTopMD5Bits &&
                    fBottomMD5Bits == other.fBottomMD5Bits );
        }
    }
}
