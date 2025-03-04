package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
        int numPhysPages = Machine.processor().getNumPhysPages();
        pageTable = new TranslationEntry[numPhysPages];
        for (int i=0; i<numPhysPages; i++) {
            pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
        }
        // Initialize stdin and stdout
        fileNames[fdStandardInput] = "stdin";
        files[fdStandardInput] = UserKernel.console.openForReading();
    
        fileNames[fdStandardOutput] = "stdout";
        files[fdStandardOutput] = UserKernel.console.openForWriting();    
        cprocesses = new LinkedList<UserProcess>(); // childProcesses
		pprocess = null; // parentProcesses
		estats = new HashMap<Integer,Integer>(); // exitStatuses
		mlock = new Lock(); //mapLock
    }
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	    return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
	new UThread(this).setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
        public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
        int end = vaddr + length;
        if(length > Machine.process().makeAddress(numPages-1, pageSize-1)- vaddr){
            length = Machine.process().makeAddress(numPages-1, pageSize-1)- vaddr;
        }

        if(vaddr < 0){
            vaddr = 0;
        }
        

        byte[] memory = Machine.processor().getMemory();
        
        int fvpage = Machine.processor().pageFromAddress(vaddr);
        int lvpage = Machine.processor().pageFromAddress(end);
        
        

        int bytesTrans = 0;

        for(int i = fvpage; i <= lvpage;i++){
            if(!pageTable[i].valid){
                return bytesTrans;
            }

            int voffset = 0;
            int poffset = pageSize - 1;

            int fvaddress = Machine.processor().makeAddress(i, 0);
            int lvaddress = Machine.processor().makeAddress(i, pageSize - 1);
            
            // the first page
            if(vaddr > fvpage && end >= lvaddress){
                voffset = vaddr - fvaddress;
            }
            // the middle page
            else if(vaddr <= fvaddress && end >= lvaddress){
                // keep defaults 
            }
            // the last page 
            else if(vaddr <= fvaddress && end < lvaddress){
                poffset = end - fvaddress;
            }
            //special case
            else{
                voffset = vaddr - fvaddress;
                poffset = end - fvaddress;
            }

            int fpaddress = Machine.processor().makeAddress(pageTable[i].ppn, voffset);
            System.arraycopy(memory, fpaddress, data, offset+bytesTrans, poffset-voffset);

            bytesTrans += (offset2-offset1);
            pageTable[i].used = true;

        }

        return bytesTrans;

    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
    int end = vaddr + length;

    if(length > Machine.process().makeAddress(numPages-1, pageSize-1)- vaddr){
        length = Machine.process().makeAddress(numPages-1, pageSize-1)- vaddr;
    }

    if(vaddr < 0){
        vaddr = 0;
    }
    
    int fvpage = Machine.processor().pageFromAddress(vaddr);
    int lvpage = Machine.processor().pageFromAddress(end);
    
    

    int bytesTrans = 0;

    for(int i = fvpage; i <= lvpage;i++){
        if(!pageTable[i].valid || pageTable[i].readOnly){
            return bytesTrans;
        }

        int voffset = 0;
        int poffset = pageSize - 1;

        int fvaddress = Machine.processor().makeAddress(i, 0);
        int lvaddress = Machine.processor().makeAddress(i, pageSize - 1);
        
        // the first page
        if(vaddr > fvpage && end >= lvaddress){
            voffset = vaddr - fvaddress;
        }
        // the middle page
        else if(vaddr <= fvaddress && end >= lvaddress){
            // keep defaults 
        }
        // the last page 
        else if(vaddr <= fvaddress && end < lvaddress){
            poffset = end - fvaddress;
        }
        //special case
        else{
            voffset = vaddr - fvaddress;
            poffset = end - fvaddress;
        }

        int fpaddress = Machine.processor().makeAddress(pageTable[i].ppn, voffset);
        System.arraycopy(data, offset+bytesTrans, memory, fpaddress, poffset - voffset);

        bytesTrans += (offset2-offset1);
        pageTable[i].used = true;
        pageTable[i].dirty = true;

    }

    return bytesTrans;

    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;
 
	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
	if (numPages > Machine.processor().getNumPhysPages()) {
	    coff.close();
	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
	    return false;
	}

	// load sections
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    
	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		      + " section (" + section.getLength() + " pages)");

	    for (int i=0; i<section.getLength(); i++) {
		int vpn = section.getFirstVPN()+i;

		section.loadPage(i, pageTable[vpn].ppn);
	    }
	}
	
	return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        UserKernel.lock.aquire();

        for(int i = 0 ; i < numPages; i++){
            UserKernel.availablePages.add(pageTable[i].ppn);
        }
        
        UserKernel.lock.release();

        for(int i = 0; i < 16; i++){
            if(FileDescriptorTable[i] != null){
                FileDescriptorTable[i].close();
            }
        }
        
        coff.close();
    }      

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

    public int getNextFileDescriptor() {
        for (int i = 2; i < MAX_FD; i++) {
            if (files[i] == null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {

	Machine.halt();
	
	Lib.assertNotReached("Machine.halt() did not halt machine!");
	return 0;
    }
    // START OF PART 3
    
 	private int handleExit(int stat){ // status
 		coff.close();
 		if (pprocess != null)
 		{
 			pprocess.mlock.acquire();
 			pprocess.estats.put(pid, stat);
 			pprocess.mlock.release();
 			pprocess.cprocesses.remove(this);
 		}
 		unloadSections();
 		if (pid == 0) {
 			Kernel.kernel.terminate(); //root exiting
 		} else {
 			UThread.finish();
 		}
 		return stat;
 	}

 	private int handleJoin(int pid, int saddr){ //
 		UserProcess cprocess = null;

 		// process matching with its pid
 		for (int i = 0; i < cprocesses.size(); i++) {
 			if(pid == cprocesses.get(i).pid) {
 				cprocess = cprocesses.get(i);
 				break;
 			}
 		}
 		
 		// pid does not match child
 		if(cprocess == null) {
 			return -1;
 		}
 		
 		cprocess.thread.join();
		mlock.acquire();
		Integer stat = estats.get(cprocess.pid);
 		mlock.release();
 		
 		// remove the child
 		cprocesses.remove(cprocess);
 		cprocess.pprocess = null;
 		
 		byte[] cstat = new byte[4];
 		Lib.bytesFromInt(cstat, 0, stat);
 		int numWrittenBytes = writeVirtualMemory(saddr, cstat); // (statusAddress, childStatus)
 		if (numWrittenBytes != 4){
 			return 0; // child not exited
 			}
 		else
 		{
 			return 1; // child exited
 			}	
 		}

 	private int handleExec(int filenameaddr, int argc, int argAddress){
 		
		String filename = readVirtualMemoryString(filenameaddr, 256);
		if (filename == null || filenameaddr < 0){    // Check string filename
				return -1;
		}

		// Check arguments
		if (argc < 0){
				return -1;
		}
		String[] argument = new String[argc];
		for(int i=0; i < argc; i++ ){
			byte[] argsAddress = new byte[4];
			int byteRead = readVirtualMemory(argAddress + (i*4), argsAddress);
			if (byteRead != 4){    	// check argsAddress
					return -1;
			}
			int argvaddr = Lib.bytesToInt(argsAddress, 0);
			
			String argum = readVirtualMemoryString(argvaddr, 256);
			if (argum == null){    // check argum
					return -1;
			}
			argument[i] = argum;
		}

		UserProcess cprocess = UserProcess.newUserProcess();
		if (cprocess.execute(filename, argument)){
			cprocess.pprocess = this; // parent process
			this.cprocesses.add(cprocess);
			return cprocess.pid;
		}else{
			return -1;
		} 	
}


 	
 	// END OF PART 3
    /**
     * Attempt to open the named disk file, creating it if it does not exist,
     * and return a file descriptor that can be used to access the file.
     *
     * Note that creat() can only be used to create files on disk; creat() will
     * never return a file descriptor referring to a stream.
     *
     * Returns the new file descriptor, or -1 if an error occurred.
     */
    private int handleCreate(int a0) {
        String fileName = readVirtualMemoryString(a0, MAX_STRING_LEN);
        OpenFile file = UserKernel.fileSystem.open(fileName, true);

        if (file != null) {
            int fd = getNextFileDescriptor();
            if (fd != -1) {
                files[fd] = file;
                fileNames[fd] = fileName;
                fileOffsets[fd] = 0;
                return fd;
            } else {
                return -1;
            }
        }
        
        return -1;
    }

    /**
     * Attempt to open the named file and return a file descriptor.
     *
     * Note that open() can only be used to open files on disk; open() will never
     * return a file descriptor referring to a stream.
     *
     * Returns the new file descriptor, or -1 if an error occurred.
     */
    private int handleOpen(int a0) {
        String fileName = readVirtualMemoryString(a0, MAX_STRING_LEN);
        OpenFile file = UserKernel.fileSystem.open(fileName, false);

        if (file != null) {
            int fd = getNextFileDescriptor();
            if (fd != -1) {
                files[fd] = file;
                fileNames[fd] = fileName;
                fileOffsets[fd] = 0;
                return fd;
            } else {
                return -1;
            }
        }

        return -1;
    }

    /**
     * Attempt to read up to count bytes into buffer from the file or stream
     * referred to by fileDescriptor.
     *
     * On success, the number of bytes read is returned. If the file descriptor
     * refers to a file on disk, the file position is advanced by this number.
     *
     * It is not necessarily an error if this number is smaller than the number of
     * bytes requested. If the file descriptor refers to a file on disk, this
     * indicates that the end of the file has been reached. If the file descriptor
     * refers to a stream, this indicates that the fewer bytes are actually
     * available right now than were requested, but more bytes may become available
     * in the future. Note that read() never waits for a stream to have more data;
     * it always returns as much as possible immediately.
     *
     * On error, -1 is returned, and the new file position is undefined. This can
     * happen if fileDescriptor is invalid, if part of the buffer is read-only or
     * invalid, or if a network stream has been terminated by the remote host and
     * no more data is available.
     */
    private int handleRead(int fd, int address, int size) {
        // Check for invalid argument
        if (fd < 0 || fd >= MAX_FD || files[fd] == null) {
            return -1;
        }

        byte buffer[] = new byte[size];

        OpenFile currentFile = files[fd];
        int bytesRead = currentFile.read(fileOffsets[fd], buffer, 0, size);

        if (bytesRead >= 0) {
            writeVirtualMemory(address, buffer);
            fileOffsets[fd] += bytesRead;
            return bytesRead;
        }

        return -1;
    }

    /**
     * Attempt to write up to count bytes from buffer to the file or stream
     * referred to by fileDescriptor. write() can return before the bytes are
     * actually flushed to the file or stream. A write to a stream can block,
     * however, if kernel queues are temporarily full.
     *
     * On success, the number of bytes written is returned (zero indicates nothing
     * was written), and the file position is advanced by this number. It IS an
     * error if this number is smaller than the number of bytes requested. For
     * disk files, this indicates that the disk is full. For streams, this
     * indicates the stream was terminated by the remote host before all the data
     * was transferred.
     *
     * On error, -1 is returned, and the new file position is undefined. This can
     * happen if fileDescriptor is invalid, if part of the buffer is invalid, or
     * if a network stream has already been terminated by the remote host.
     */
    private int handleWrite(int fd, int address, int size) {
        // Check for invalid argument
        if (fd < 0 || fd >= MAX_FD || files[fd] == null) {
            return -1;
        }

        byte buffer[] = new byte[size];

        OpenFile currentFile = files[fd];
        int bytesWritten = currentFile.write(fileOffsets[fd], buffer, 0, size);

        if (bytesWritten >= 0) {
            readVirtualMemory(address, buffer);
            fileOffsets[fd] += bytesWritten;
            return bytesWritten;
        }

        return -1;
    }

    /**
     * Close a file descriptor, so that it no longer refers to any file or stream
     * and may be reused.
     *
     * If the file descriptor refers to a file, all data written to it by write()
     * will be flushed to disk before close() returns.
     * If the file descriptor refers to a stream, all data written to it by write()
     * will eventually be flushed (unless the stream is terminated remotely), but
     * not necessarily before close() returns.
     *
     * The resources associated with the file descriptor are released. If the
     * descriptor is the last reference to a disk file which has been removed using
     * unlink, the file is deleted (this detail is handled by the file system
     * implementation).
     *
     * Returns 0 on success, or -1 if an error occurred.
     */
    private int handleClose(int fd) {
        // Check for invalid argument
        if (fd < 0 || fd >= MAX_FD || files[fd] == null) {
            return -1;
        }
    
        files[fd].close();

        boolean success = true;
        if (removeFile[fd]) {
            success = UserKernel.fileSystem.remove(fileNames[fd]);
            removeFile[fd] = false;
        }
        fileNames[fd] = null;

        return success ? 0 : -1;
    }

    /**
     * Delete a file from the file system. If no processes have the file open, the
     * file is deleted immediately and the space it was using is made available for
     * reuse.
     *
     * If any processes still have the file open, the file will remain in existence
     * until the last file descriptor referring to it is closed. However, creat()
     * and open() will not be able to return new file descriptors for the file
     * until it is deleted.
     *
     * Returns 0 on success, or -1 if an error occurred.
     */
    private int handleUnlink(int a0) {
        String fileName = readVirtualMemoryString(a0, MAX_STRING_LEN);

        int fd = -1;
        boolean success = true;
        if (fd != -1) {
            success = UserKernel.fileSystem.remove(fileName);
        } else {
            removeFile[fd] = true;
        }
        return success ? 0 : -1;
    }

    private static final int
    syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
            case syscallHalt:
                return handleHalt();
            case syscallExit:
                //return handleExit(a0);
            case syscallExec:
                //return handleExec(a0, a1, a2);
            case syscallJoin:
                //return handleJoin(a0, a1);
            case syscallCreate:
                return handleCreate(a0);
            case syscallOpen:
                return handleOpen(a0);
            case syscallRead:
                return handleRead(a0, a1, a2);           
            case syscallWrite:
                return handleWrite(a0, a1, a2);
            case syscallClose:
                return handleClose(a0);            
            case syscallUnlink:
                return handleUnlink(a0);
            default:
            Lib.debug(dbgProcess, "Unknown syscall " + syscall);
            Lib.assertNotReached("Unknown system call!");
        }
        return 0;
    }

    

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    Lib.assertNotReached("Unexpected exception");
	}
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv;
	
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';

    // File descriptors of stdin and stdout
    private static final int fdStandardInput = 0;
    private static final int fdStandardOutput = 1;
    //Part 3
    private LinkedList<UserProcess> cprocesses;
	private UserProcess pprocess;
	private int pid;
	private Lock mlock;
	private HashMap<Integer,Integer> estats;
	private UThread thread;
    /// Index of the 3 arrays act as file descriptors
    // Array of files
    private static final int MAX_FD = 16;
    private static OpenFile files[] = new OpenFile[MAX_FD];
    // Array of fileNames
    private static final int MAX_STRING_LEN = 256;
    private static String fileNames[] = new String[MAX_FD];
    // Array of fileOffsets
    private static int fileOffsets[] = new int[MAX_FD];
    // Array of removeFiles
    private static boolean removeFile[] = new boolean[MAX_FD];
}
