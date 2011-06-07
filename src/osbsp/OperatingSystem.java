package osbsp;

/*
 * OperatingSystem.java
 */
import java.util.*;

/**
 * Basisfunktionen eines 32-Bit Betriebssystems System Calls: createProcess,
 * killAll, write, read
 * 
 */
public class OperatingSystem {
	// ------------ Vordefinierte Prozess-Parameter -----------------------
	/**
	 * max. Anzahl Seiten pro Prozess im Hauptspeicher (sonst Verdr�ngung
	 * eigener Seiten):
	 */
	private int MAX_RAM_PAGES_PER_PROCESS = 10;

	/**
	 * max. Anzahl Prozesse (muss beschr�nkt werden, da kein Swapping
	 * implementiert ist!): (ein Teil des Hauptspeichers muss immer frei bleiben
	 * (u.a. f�r Caching etc.), daher -PAGE_SIZE)
	 */
	private int MAX_NUM_OF_PROCESSES = (RAM_SIZE - PAGE_SIZE)
			/ (MAX_RAM_PAGES_PER_PROCESS * PAGE_SIZE);

	/**
	 * Dieser Faktor bestimmt das "Lokalit�tsverhalten" eines Programms (=
	 * Anzahl Operationen innerhalb eines Seitenbereichs)
	 */
	private int DEFAULT_LOCALITY_FACTOR = 30;

	// ------------ Konfigurierbare maschinenabh�ngige Parameter
	// -----------------------
	// L�nge eines Datenworts in Byte
	private static final int WORD_SIZE = 4;
	// 2^16 Byte = 64 KByte RAM
	private static final int RAM_SIZE = 65536;
	// 2^8 Byte = 256 Byte Seitengr��e --> max. 2^8 = 256 Seitenrahmen , 64
	// Worte pro Seitenrahmen
	private static final int PAGE_SIZE = 256;
        // logarithmus PAGE_SIZE, Potenz
        private static final int PAGE_SIZE_POTENZ = (int)lb(PAGE_SIZE);
	// Virtueller Adressraum: 2^20 Byte = 1 MByte (max. virt. Adresse)
	private static final int VIRT_ADR_SPACE = 1048576;

	// ------------ Abgeleitete maschinenabh�ngige Parameter
	// -----------------------
	// Max. Anzahl virtueller Seiten: 2^12 = 4096 Seiten
	private static final int MAX_NO_OF_PAGES = VIRT_ADR_SPACE / PAGE_SIZE;
	// Platteneigenschaften:
	// Gr��e = virt. Adressraum reicht hier, weil wir keine weiteren Dateien
	// brauchen
	private static final int DISK_SIZE = VIRT_ADR_SPACE;
	// Gr��e eines Plattenblocks
	private static final int BLOCK_SIZE = PAGE_SIZE;

	// ------------ Hardware-Stubs --------------------------------------
	// Physikalischer Hauptspeicher
	private Hashtable<Integer, Integer> physRAM;
	// Physikalische Festplatte
	private Hashtable<Integer, Integer> physDisk;

	// ---------- Systemtabellen ----------------------------------------
	// Freibereichsliste Hauptspeicher
	private LinkedList<FreeListBlock> ramFreeList;
	// Freibereichsliste Festplatte
	private LinkedList<FreeListBlock> diskFreeList;
	// Liste aller Prozesse
	private LinkedList<Process> processList;
	private int processCounter;

	/**
	 * Zeiger auf Statistik-Objekt
	 */
	public Statistics eventLog; // Protokollierung und statistische Auswertung

	// -------------------------- Teststeuerung -----------------------------
	private boolean TEST = false; // Testausgaben erw�nscht?

	// -------------------------- Seitenersetzungs-Algorithmus
	// -----------------------------

	/**
	 * Symbolische Konstante f�r Clock-Algorithmus, Wert = 0
	 */
	public final int CLOCK = 0;
	/**
	 * Symbolische Konstante f�r Fifo-Algorithmus, Wert = 1
	 */
	public final int FIFO = 1;

	/**
	 * Symbolische Konstante f�r Random-Algorithmus, Wert = 2
	 */
	public final int RANDOM = 2;

	/**
	 * Auswahl des Seitenersetzungs-Algorithmus
	 */
	private int REPLACEMENT_ALGORITHM = CLOCK;

	// ------------------------- Public-Methoden ---------------------------
	/**
	 * Konstruktor
	 */        
	public OperatingSystem() {
		// RAM initialisieren (Zugriffe erfolgen wortweise!)
		physRAM = new Hashtable<Integer, Integer>(RAM_SIZE / WORD_SIZE);
		// RAM - Freibereichsliste initialisieren
		ramFreeList = new LinkedList<FreeListBlock>();
		FreeListBlock ramFB = new FreeListBlock(0, RAM_SIZE);
		ramFreeList.add(ramFB);

		// Platte initialisieren (Zugriffe erfolgen blockweise!))
		physDisk = new Hashtable<Integer, Integer>(DISK_SIZE / BLOCK_SIZE);
		// Platten - Freibereichsliste initialisieren
		diskFreeList = new LinkedList<FreeListBlock>();
		FreeListBlock diskFB = new FreeListBlock(0, DISK_SIZE);
		diskFreeList.add(diskFB);

		// Prozessliste initialisieren
		processList = new LinkedList<Process>();
		processCounter = 0;

		// Statistische Protokollierung aktivieren
		eventLog = new Statistics();
	}

	/**
	 * Prozess-Objekt (Thread) erzeugen und in Prozessliste eintragen
	 * 
	 * @param die
	 *            Gr��e des Prozess-Hauptspeicherbedarfs in Byte
	 * 
	 * @return die neue Prozess-ID oder -1, wenn Erzeugung nicht m�glich
	 *         (Speichermangel)
	 */
	public synchronized int createProcess(int processSize) {
		if (processList.size() < MAX_NUM_OF_PROCESSES) {
			// RAM-Platz f�r neuen Prozess vorhanden
			Process proc = new Process(this, processCounter, processSize);
			processList.add(proc);
			System.out.println("Prozess " + proc.pid + " wurde erzeugt!");
			// Prozess in den Hauptspeicher "laden"
			loadProcess(processCounter, processSize);
			// Prozess als JAVA-Thread starten
			proc.start();
			processCounter++; // Neue Prozess-IDs werden hochgez�hlt
			return proc.pid;
		} else {
			// RAM voll
			return -1;
		}
	}

	private void loadProcess(int pid, int processSize) {
		// Laden des Programmtextes und initialisieren der Datenbereiche
		// Speicherbelegung durch write - Operationen
		int item; // Dummy

		for (int virtAdr = 0; virtAdr < processSize; virtAdr = virtAdr
				+ getWORD_SIZE()) {
			// Zu schreibendes Datenwort bestimmen
			item = (int) (Math.pow(2, 31) * Math.random());
			// System Call
			write(pid, virtAdr, item);
		}
		System.out.println("Prozess " + pid + ": " + processSize + " Byte ("
				+ processSize / getPAGE_SIZE()
				+ " Seiten) in den Speicher geladen!");
		// Statistikz�hler neu initialisieren
		eventLog.resetCounter();
	}

	/**
	 * Alle aktiven Prozesse aus Prozessliste beenden
	 */
	public synchronized void killAll() {
		Process proc;
		int i;

		for (i = 0; i < processList.size(); i++) {
			proc = (Process) processList.get(i);
			System.out.println("Prozess " + proc.pid + " wird unterbrochen!");
			proc.interrupt();
		}
	}

	/**
	 * Datenwort item auf eine virtuelle Adresse virtAdr im virtuellen Speicher
	 * schreiben
	 * 
	 * @param pid
	 *            Prozess-ID
	 * @param virtAdr
	 *            virtuelle Adresse
	 * @param item
	 *            Datenwort
	 * @return 0 wenn Schreiboperation erfolgreich oder -1 bei fehlerhafter
	 *         Adresse
	 */
	public synchronized int write(int pid, int virtAdr, int item) {
		int virtualPageNum; // Virtuelle Seitennummer
		int offset; // Offset innerhalb der Seite
		int realAddressOfItem; // Reale Adresse des Datenworts
		Process proc; // Aktuelles Prozessobjekt
		PageTableEntry pte; // Eintrag f�r die zu schreibende Seite

		// �bergebene Adresse pr�fen
		if ((virtAdr < 0) || (virtAdr > VIRT_ADR_SPACE - WORD_SIZE)) {
			System.err.println("OS: write ERROR " + pid + ": Adresse "
					+ virtAdr
					+ " liegt au�erhalb des virtuellen Adressraums 0 - "
					+ VIRT_ADR_SPACE);
			return -1;
		}
		// Seitenadresse berechnen
		virtualPageNum = getVirtualPageNum(virtAdr);
		offset = getOffset(virtAdr);
		testOut("OS: write " + pid + " " + virtAdr + " " + item
				+ " +++ Seitennr.: " + virtualPageNum + " Offset: " + offset);

		// Seite in Seitentabelle referenzieren
		proc = getProcess(pid);
		pte = proc.pageTable.getPte(virtualPageNum);
		if (pte == null) {
			// Seite nicht vorhanden:
			testOut("OS: write " + pid + " +++ Seitennr.: " + virtualPageNum
					+ " in Seitentabelle nicht vorhanden");
			pte = new PageTableEntry();
			pte.virtPageNum = virtualPageNum;
			// Seitenrahmen im RAM f�r die neue Seite anfordern und reale
			// (RAM-)SeitenAdresse eintragen
			pte.realPageFrameAdr = getNewRAMPage(pte, pid);
			pte.valid = true;
			// neue Seite in Seitentabelle eintragen
			proc.pageTable.addEntry(pte);
			testOut("OS: write " + pid + " Neue Seite " + virtualPageNum
					+ " in Seitentabelle eingetragen! RAM-Adr.: "
					+ pte.realPageFrameAdr);
		} else {
			// Seite vorhanden: Seite valid (im RAM)?
			if (!pte.valid) {
				// Seite nicht valid (also auf Platte --> Seitenfehler):
				pte = handlePageFault(pte, pid);
			}
		}
		// ------ Zustand: Seite ist in Seitentabelle und im RAM vorhanden

		// Reale Adresse des Datenworts berechnen
		realAddressOfItem = pte.realPageFrameAdr + offset;
		// Datenwort in RAM eintragen
		writeToRAM(realAddressOfItem, item);
		testOut("OS: write " + pid + " +++ item: " + item
				+ " erfolgreich an virt. Adresse " + virtAdr
				+ " geschrieben! RAM-Adresse: " + realAddressOfItem + " \n");
		// Seitentabelle bzgl. Zugriffshistorie aktualisieren
		pte.referenced = true;
		// Statistische Z�hlung
		eventLog.incrementWriteAccesses();
		return 0;
	}

	/**
	 * Datenwort von einer Adresse im virtuellen Speicher lesen
	 * 
	 * @param pid
	 *            Prozess-ID
	 * @param virtAdr
	 *            virtuelle Adresse
	 * @return Datenwort auf logischer Adresse virtAdr oder -1 bei
	 *         Zugriffsfehler
	 */
	public synchronized int read(int pid, int virtAdr) {
		int virtualPageNum; // Virtuelle Seitennummer
		int offset; // Offset innerhalb der Seite
		int realAddressOfItem; // Reale Adresse des Datenworts
                int item; // item das aus dem Speicher gelesen wird.
		Process proc; // Aktuelles Prozessobjekt
		PageTableEntry pte; // Eintrag f�r die zu schreibende Seite

		// �bergebene Adresse pr�fen
		if ((virtAdr < 0) || (virtAdr > VIRT_ADR_SPACE - WORD_SIZE)) {
			System.err.println("OS: write ERROR " + pid + ": Adresse "
					+ virtAdr
					+ " liegt au�erhalb des virtuellen Adressraums 0 - "
					+ VIRT_ADR_SPACE);
			return -1;
		}
		// Seitenadresse berechnen
		virtualPageNum = getVirtualPageNum(virtAdr);
		offset = getOffset(virtAdr);
		testOut("OS: read " + pid + " " + virtAdr + " "
				+ " +++ Seitennr.: " + virtualPageNum + " Offset: " + offset);

		// Seite in Seitentabelle referenzieren
		proc = getProcess(pid);
		pte = proc.pageTable.getPte(virtualPageNum);
		if (pte == null) {
			// Seite nicht vorhanden:
			testOut("OS: write " + pid + " +++ Seitennr.: " + virtualPageNum
					+ " in Seitentabelle nicht vorhanden");
			pte = new PageTableEntry();
			pte.virtPageNum = virtualPageNum;
			// Seitenrahmen im RAM f�r die neue Seite anfordern und reale
			// (RAM-)SeitenAdresse eintragen
			pte.realPageFrameAdr = getNewRAMPage(pte, pid);
			pte.valid = true;
			// neue Seite in Seitentabelle eintragen
			proc.pageTable.addEntry(pte);
			testOut("OS: write " + pid + " Neue Seite " + virtualPageNum
					+ " in Seitentabelle eingetragen! RAM-Adr.: "
					+ pte.realPageFrameAdr);
		} else {
			// Seite vorhanden: Seite valid (im RAM)?
			if (!pte.valid) {
				// Seite nicht valid (also auf Platte --> Seitenfehler):
				pte = handlePageFault(pte, pid);
			}
		}
		// ------ Zustand: Seite ist in Seitentabelle und im RAM vorhanden
                
                // Reale Adresse des Datenworts berechnen
		realAddressOfItem = pte.realPageFrameAdr + offset;
                
                // Datenwort in RAM eintragen
		item = readFromRAM(realAddressOfItem);
		testOut("OS: read " + pid + " +++ item: " + item
				+ " erfolgreich an virt. Adresse " + virtAdr
				+ " geschrieben! RAM-Adresse: " + realAddressOfItem + " \n");
		// Seitentabelle bzgl. Zugriffshistorie aktualisieren
		pte.referenced = true;
		// Statistische Z�hlung
		eventLog.incrementReadAccesses();
		return item;
	}
        
        private static double lb( double x ) 
        { 
            return Math.log( x ) / Math.log( 2.0 ); 
        }
        
	// --------------- Private Methoden des Betriebssystems
	// ---------------------------------

	/**
	 * @param pid
	 * @return Prozess-Objekt f�r die Prozess-ID
	 */
	private Process getProcess(int pid) {
		return (Process) processList.get(pid);
	}

	/**
	 * @param virtAdr
	 *            : eine virtuelle Adresse
	 * @return Die entsprechende virtuelle Seitennummer
	 */
	private int getVirtualPageNum(int virtAdr) {
                    
                int pagenum = virtAdr >> PAGE_SIZE_POTENZ;
                
                //int pagenum = virtAdr / PAGE_SIZE; // Virtuelle Seite errechnen. Offset wird automatisch abgeschnitten. 
                
                return pagenum;
	}

	/**
	 * @param virtAdr
	 *            : eine virtuelle Adresse
	 * @return Den entsprechenden Offset zur Berechnung der realen Adresse
	 */
	private int getOffset(int virtAdr) {
                
                int offset = virtAdr & (int)(Math.pow(2, PAGE_SIZE_POTENZ));
      		//int offset = virtAdr % PAGE_SIZE; // Offset berechnen. Durch den Modulo-Operator bleibt nur der Offset über.
                
                return offset;
	}

	/**
	 * Behandlung eines Seitenfehlers f�r die durch den pte beschriebene Seite
	 * 
	 * @param pte
	 *            Seitentabelleneintrag
	 * @param pid
	 *            Prozess-Id
	 * @return modifizierter Seitentabelleneintrag
	 */
	private PageTableEntry handlePageFault(PageTableEntry pte, int pid) {
		int newPageFrameAdr; // Reale Adresse einer neuen Seite im RAM

		testOut("OS: " + pid + " +++ Seitenfehler f�r Seite " + pte.virtPageNum);
		eventLog.incrementPageFaults(); // Statistische Z�hlung
		// neue Seite im RAM anfordern (ggf. alte Seite verdr�ngen!)
		newPageFrameAdr = getNewRAMPage(pte, pid);
		// Seite von Platte in neue RAM-Seite lesen (realPageAdr muss
		// Plattenblockadresse gewesen sein!)
		dataTransferFromDisk(pte.realPageFrameAdr, newPageFrameAdr);
		// Plattenblock freigeben
		freeDiskBlock(pte.realPageFrameAdr);
		// Seitentabelle aktualisieren
		pte.realPageFrameAdr = newPageFrameAdr;
		pte.valid = true;
		testOut("OS: " + pid + " +++ Seite " + pte.virtPageNum
				+ " ist wieder im RAM an Adresse " + pte.realPageFrameAdr);

		return pte;
	}

	/**
	 * Leere RAM-Seite zur Verf�gung stellen (ggf. alte Seite auslagern)
	 * 
	 * @param pid
	 *            Prozess-Id
	 * @return Reale RAM-Adresse einer neuen und freien Seite
	 */
	private int getNewRAMPage(PageTableEntry newPte, int pid) {
		// Algorithmus:
		// Anforderung einer neuen RAM-Seite f�r die gegebene newPte erf�llbar?
		// (< MAX_RAM_PAGES_PER_PROCESS)
		// Ja, Seitenanforderung im RAM ist erf�llbar:
		// neue Seite belegen und Adresse zur�ckgeben
		// Nein, Seitenanforderung im RAM ist nicht erf�llbar:
		// eine alte Seite zur Verdr�ngung ausw�hlen -->
		// Seitenersetzungs-Algorithmus
		// alte Seite
		// auf Platte auslagern (neuen Diskblock anfordern)
		// im RAM l�schen (mit Nullen �berschreiben)
		// Adresse als neue Seite zur�ckgeben
		// ----------- Start ----------------
		Process proc; // Aktuelles Prozessobjekt
		int newPageFrameAdr = 0; // Reale Adresse einer neuen Seite im RAM
		int replacePageFrameAdr = 0; // Reale Adresse einer zu ersetzenden Seite
		int newDiskBlock = 0; // Reale Adresse eines neuen Plattenblocks
		PageTableEntry replacePte; // Eintrag f�r eine ggf. zu ersetzende Seite

		proc = getProcess(pid);
		// Anforderung einer neuen RAM-Seite erf�llbar?
		if (proc.pageTable.getSize() < MAX_RAM_PAGES_PER_PROCESS) {
			// Ja, Seitenanforderung im RAM ist erf�llbar:
			// neue Seite belegen und Adresse zur�ckgeben
			newPageFrameAdr = allocateRAMPage();
			// Liste der RAM-Seiten f�r den Prozess erweitern
			proc.pageTable.pteRAMlistInsert(newPte);
		} else {
			// Nein, Seitenanforderung im RAM ist nicht erf�llbar:
			testOut("OS: getNewRAMPage " + pid + " ++ Seitenfehler f�r Seite "
					+ newPte.virtPageNum + " --> Seitenersetzungs-Algorithmus!");
			// eine alte Seite zur Verdr�ngung ausw�hlen -->
			// Seitenersetzungs-Algorithmus
			replacePte = proc.pageTable.selectNextRAMpteAndReplace(newPte);
			replacePageFrameAdr = replacePte.realPageFrameAdr;
			// alte Seite auf Platte auslagern (vorher neuen Diskblock
			// anfordern)
			// hier: IMMER zur�ckschreiben, weil keine Kopie auf der Platte
			// bleibt
			// (M-Bit wird also nicht benutzt!)
			newDiskBlock = allocateDiskBlock();
			dataTransferToDisk(replacePageFrameAdr, newDiskBlock);
			// alte Seite im RAM l�schen
			freeRAMPage(replacePageFrameAdr);
			// Plattenadresse in Seitentabelle eintragen
			replacePte.realPageFrameAdr = newDiskBlock;
			replacePte.valid = false;

			testOut("OS: getNewRAMPage " + pid + " ++ Seite "
					+ replacePte.virtPageNum
					+ " ist nun auf der Platte an Adresse "
					+ replacePte.realPageFrameAdr);
			// Adresse als neue Seite zur�ckgeben
			newPageFrameAdr = replacePageFrameAdr;
		}
		return newPageFrameAdr;
	}

	/**
	 * Schreibe das item an der realen Adresse ramAdr in den RAM
	 * 
	 * @param ramAdr
	 * @param item
	 */
	private void writeToRAM(int ramAdr, int item) {
		physRAM.put(new Integer(ramAdr), new Integer(item));
	}

	/**
	 * Lies das item an der realen Adresse ramAdr aus dem RAM
	 * 
	 * @param ramAdr
	 * @return das item als positive Integerzahl oder -1, falls Adresse nicht
	 *         belegt
	 */
	private int readFromRAM(int ramAdr) {
		Integer itemObject;
		int result;

		itemObject = (Integer) physRAM.get(new Integer(ramAdr));
		if (itemObject == null) {
			result = -1;
		} else {
			result = itemObject.intValue();
		}
		return result;
	}

	/**
	 * Schreibe die Seite an der realen RAM-Adresse ramAdr auf die Platte unter
	 * der Adresse diskAdr
	 * 
	 * @param ramAdr
	 * @param diskAdr
	 */
	private void dataTransferToDisk(int ramAdr, int diskAdr) {

		Integer currentWord; // aktuelles Speicherwort
		int ri; // aktuelle Speicherwortadresse im RAM
		int di; // aktuelle Speicherwortadresse auf der Platte

		di = diskAdr;
		for (ri = ramAdr; ri < ramAdr + PAGE_SIZE; ri = ri + WORD_SIZE) {
			currentWord = (Integer) physRAM.get(new Integer(ri));
			physDisk.put(new Integer(di), currentWord);
			di = di + WORD_SIZE;
		}
	}

	/**
	 * Schreibe den Plattenblock an der realen Plattenadresse diskAdr in den RAM
	 * unter der Adresse ramAdr
	 * 
	 * @param diskAdr
	 * @param ramAdr
	 */
	private void dataTransferFromDisk(int diskAdr, int ramAdr) {
		Integer currentWord; // aktuelles Speicherwort
		int ri; // aktuelle Speicherwortadresse im RAM
		int di; // aktuelle Speicherwortadresse auf der Platte

		ri = ramAdr;
		for (di = diskAdr; di < diskAdr + BLOCK_SIZE; di = di + WORD_SIZE) {
			currentWord = (Integer) physDisk.get(new Integer(di));
			physRAM.put(new Integer(ri), currentWord);
			ri = ri + WORD_SIZE;
		}
	}

	/**
	 * Liefere eine freie RAM-Seite und l�sche sie aus der RAM-Freibereichsliste
	 * 
	 * @return reale Adresse einer freien RAM-Seite
	 */
	private int allocateRAMPage() {
		// Algorithmus:
		// 1. Block der Freibereichsliste um PAGE_SIZE verkleinern.
		// Falls size = 0 --> l�schen!
		FreeListBlock ramFB; // Erster Block aus Freibereichsliste
		int freePageAdr; // R�ckgabeadresse

		ramFB = (FreeListBlock) ramFreeList.getFirst();
		freePageAdr = ramFB.getAdress();
		// Block in Freibereichsliste aktualisieren
		if (ramFB.getSize() == PAGE_SIZE) {
			// Block w�re anschlie�end leer --> L�schen
			ramFreeList.removeFirst();
		} else {
			ramFB.setAdress(freePageAdr + PAGE_SIZE);
			ramFB.setSize(ramFB.getSize() - PAGE_SIZE);
		}
		testOut("OS: new RAM Page allocated at adress: " + freePageAdr);
		return freePageAdr;
	}

	/**
	 * L�sche eine RAM-Seite und trage sie in die RAM-Freibereichsliste ein
	 * 
	 * @param ramAdr
	 */
	private void freeRAMPage(int ramAdr) {
		// Algorithmus:
		// RAM-Seite mit Nullen �berschreiben (Security!) und neuen
		// Freibereichsblock erzeugen
		// (Eine Zusammenfassung von Freibereichsbl�cken (Bereinigen der
		// Fragmentierung) m�sste
		// zus�tzlich implementiert werden!)
		Integer nullWord; // Null-Speicherwort
		int ri; // aktuelle Speicherwortadresse im RAM
		FreeListBlock ramFB; // neuer FreeListBlock

		// RAM-Seite �berschreiben
		nullWord = new Integer(0);
		for (ri = ramAdr; ri < ramAdr + PAGE_SIZE; ri = ri + WORD_SIZE) {
			physRAM.put(new Integer(ri), nullWord);
		}
		// In Freibereichsliste eintragen
		ramFB = new FreeListBlock(ramAdr, PAGE_SIZE);
		ramFreeList.add(ramFB);
		Collections.sort(ramFreeList);
		testOut("OS: RAM page released at adress: " + ramAdr);
	}

	/**
	 * Liefere einen freien Plattenblock und l�sche ihn aus der
	 * Platten-Freibereichsliste
	 * 
	 * @return reale Adresse eines freien Plattenblocks oder -1, wenn die Platte
	 *         voll ist
	 */
	private int allocateDiskBlock() {
		// Algorithmus:
		// 1. Block der Freibereichsliste um BLOCK_SIZE verkleinern. Falls size
		// = 0 --> l�schen!
		FreeListBlock diskFB; // Erster Block aus Freibereichsliste
		int freeBlockAdr; // R�ckgabeadresse

		diskFB = (FreeListBlock) diskFreeList.getFirst();
		if ((diskFreeList.size() == 1) && (diskFB.getSize() == BLOCK_SIZE)) {
			// Nur noch ein freier Block vorhanden --> Platte voll!
			testOut("OS: allocateDiskBlock: Platte ist voll! --------------------------------------- ");
			return -1;
		} else {
			freeBlockAdr = diskFB.getAdress();
			// Block in Freibereichsliste aktualisieren
			if (diskFB.getSize() == BLOCK_SIZE) {
				// Block w�re anschlie�end leer --> L�schen
				diskFreeList.removeFirst();
			} else {
				diskFB.setAdress(freeBlockAdr + BLOCK_SIZE);
				diskFB.setSize(diskFB.getSize() - BLOCK_SIZE);
			}
			testOut("OS: new disk Block allocated at adress: " + freeBlockAdr);
			return freeBlockAdr;
		}
	}

	/**
	 * L�sche einen Plattenblock und trage ihn in die Platten-Freibereichsliste
	 * ein
	 * 
	 * @param diskAdr
	 */
	private void freeDiskBlock(int diskAdr) {
		// Algorithmus:
		// Plattenblock mit Nullen �berschreiben (Security!) und neuen
		// Freibereichsblock erzeugen
		// (Eine Zusammenfassung von Freibereichsbl�cken (Bereinigen der
		// Fragmentierung) m�sste
		// zus�tzlich implementiert werden!)
		Integer nullWord; // Null-Speicherwort
		int di; // aktuelle Speicherwortadresse auf der Platte
		FreeListBlock diskFB; // neuer FreeListBlock

		// Plattenblock �berschreiben
		nullWord = new Integer(0);
		for (di = diskAdr; di < diskAdr + BLOCK_SIZE; di = di + WORD_SIZE) {
			physDisk.put(new Integer(di), nullWord);
		}
		// In Freibereichsliste eintragen
		diskFB = new FreeListBlock(diskAdr, BLOCK_SIZE);
		diskFreeList.add(diskFB);
		Collections.sort(diskFreeList);
		testOut("OS: disk Block released at adress: " + diskAdr);
	}

	// ------------------------- getter-Methoden f�r Konstanten
	// -------------------------------

	/**
	 * @return Die max. Anzahl Seiten pro Prozess im Hauptspeicher (sonst
	 *         Verdr�ngung eigener Seiten).
	 */
	public int getMAX_RAM_PAGES_PER_PROCESS() {
		return MAX_RAM_PAGES_PER_PROCESS;
	}

	/**
	 * @param i
	 *            max. Anzahl Seiten pro Prozess im Hauptspeicher (sonst
	 *            Verdr�ngung eigener Seiten)
	 */
	public void setMAX_RAM_PAGES_PER_PROCESS(int i) {
		i = Math.max(1, i);
		i = Math.min(i, MAX_NO_OF_PAGES);
		MAX_RAM_PAGES_PER_PROCESS = i;
		MAX_NUM_OF_PROCESSES = (RAM_SIZE - PAGE_SIZE)
				/ (MAX_RAM_PAGES_PER_PROCESS * PAGE_SIZE);
		testOut("OS: MAX_RAM_PAGES_PER_PROCESS: " + MAX_RAM_PAGES_PER_PROCESS
				+ " MAX_NUM_OF_PROCESSES:" + MAX_NUM_OF_PROCESSES);
	}

	/**
	 * @return Die max. Anzahl an Prozessen
	 */
	public int getMAX_NUM_OF_PROCESSES() {
		return MAX_NUM_OF_PROCESSES;
	}

	/**
	 * @return Anzahl Operationen innerhalb eines Seitenbereichs
	 */
	public int getDEFAULT_LOCALITY_FACTOR() {
		return DEFAULT_LOCALITY_FACTOR;
	}

	/**
	 * @param i
	 *            Anzahl Operationen innerhalb eines Seitenbereichs
	 */
	public void setDEFAULT_LOCALITY_FACTOR(int i) {
		i = Math.max(1, i);
		DEFAULT_LOCALITY_FACTOR = i;
	}

	/**
	 * @return Die L�nge eines Datenworts (in Byte)
	 */
	public int getWORD_SIZE() {
		return WORD_SIZE;
	}

	/**
	 * @return Die Gr��e einer Seite (in Byte)
	 */
	public int getPAGE_SIZE() {
		return PAGE_SIZE;
	}

	/**
	 * @return Die Gr��e des Hauptspeichers (in Byte)
	 */
	public int getRAM_SIZE() {
		return RAM_SIZE;
	}

	/**
	 * @return Die Gr��e des virtuellen Adressraums (in Byte)
	 */
	public int getVIRT_ADR_SPACE() {
		return VIRT_ADR_SPACE;
	}

	/**
	 * @return Die max. Anzahl an Seiten
	 * 
	 */
	public int getMAX_NO_OF_PAGES() {
		return MAX_NO_OF_PAGES;
	}

	/**
	 * @return Die Gr��e der Festplatte (in Byte)
	 */
	public int getDISK_SIZE() {
		return DISK_SIZE;
	}

	/**
	 * @return 0 = CLOCK, 1 = FIFO, 2 = RANDOM
	 */
	public int getREPLACEMENT_ALGORITHM() {
		return REPLACEMENT_ALGORITHM;
	}

	/**
	 * @param i
	 *            0 = CLOCK, 1 = FIFO, 2 = RANDOM
	 */
	public void setREPLACEMENT_ALGORITHM(int i) {
		REPLACEMENT_ALGORITHM = i;
	}

	/**
	 * @return Testausgaben erw�nscht?
	 */
	public boolean isTEST() {
		return TEST;
	}

	/**
	 * @param b
	 *            Testausgaben erw�nscht?
	 */
	public void setTEST(boolean b) {
		TEST = b;
	}

	// ------------------ Steuerung der Testausgaben
	// -----------------------------
	/**
	 * @param ausgabe
	 *            String ausgeben, falls im TEST-Modus
	 */
	public void testOut(String ausgabe) {
		if (TEST == true) {
			System.err.println(ausgabe);
		}
	}
}
