package osbsp;
import java.util.*;

/**
 * PageTable.java
 * 
 * Eine Seitentabelle eines Prozesses, implementiert als Array von
 * PageTableEntry-Elementen (pte)
 * 
 */
public class PageTable {
	private static final int PAGETABLE_MAX_SIZE = 1000;

	private PageTableEntry[] pageTableArray; // die Seitentabelle
	private int index; // Index des ersten leeren Eintrags im Array
	private LinkedList<PageTableEntry> pteRAMlist; // Liste aller Seiten, die
													// sich im RAM befinden
	private int pteRAMlistIndex; // Uhrzeiger für Clock-Algorithmus

	private OperatingSystem os;
	private int pid;

	/**
	 * Konstruktor
	 */
	public PageTable(OperatingSystem currentOS, int myPID) {
		os = currentOS;
		pid = myPID;
		pageTableArray = new PageTableEntry[PAGETABLE_MAX_SIZE]; // die
																	// Seitentabelle
		index = 0; // Index auf das nächste Element
		pteRAMlist = new LinkedList<PageTableEntry>();
		pteRAMlistIndex = 0;
	}

	/**
	 * Rückgabe: Seitentabelleneintrag pte (PageTableEntry) für die übergebene
	 * virtuelle Seitennummer (VPN = Virtual Page Number) oder null
	 */
	public PageTableEntry getPte(int vpn) {

		if ((vpn < 0) || (vpn >= index)) {
			// os.testOut("PageTable.getPte() in Prozess "+pid+": Rückgabe null,
			// da Seite "+vpn+" noch nicht existiert!");
			return null;
		} else {
			return pageTableArray[vpn];
		}
	}

	/**
	 * Einen Eintrag (PageTableEntry) an die Seitentabelle anhängen. Die
	 * Seitentabelle darf nicht sortiert werden!
	 */
	public void addEntry(PageTableEntry pte) {
		if (index < PAGETABLE_MAX_SIZE) {
			pageTableArray[index] = pte;
			// os.testOut("PageTable in Prozess "+pid+": Eintrag für Index
			// "+index+" erfolgreich erzeugt!");
			index++;
		} else {
			System.out.println("--------- Schwerer Fehler in Prozess " + pid
					+ ": PageTable overflow!!!!");
		}
	}

	/**
	 * Rückgabe: Aktuelle Größe der Seitentabelle.
	 */
	public int getSize() {
		return index;
	}

	/**
	 * Pte in pteRAMlist eintragen, wenn sich die Zahl der RAM-Seiten des
	 * Prozesses erhöht hat.
	 */
	public void pteRAMlistInsert(PageTableEntry pte) {
		os.testOut("pteRAMlistInsert in Prozess " + pid + ": pte mit vpn "
				+ pte.virtPageNum + " angefügt!");
		pteRAMlist.add(pte);
	}

	/**
	 * Eine Seite, die sich im RAM befindet, anhand der pteRAMlist auswählen und
	 * zurückgeben
	 */
	public PageTableEntry selectNextRAMpteAndReplace(PageTableEntry newPte) {
		if (os.getREPLACEMENT_ALGORITHM() == os.CLOCK) {
			return clockAlgorithm(newPte);
		} else {
			if (os.getREPLACEMENT_ALGORITHM() == os.FIFO) {
				return fifoAlgorithm(newPte);
			} else {
				return randomAlgorithm(newPte);
			}
		}
	}

	/**
	 * FIFO-Algorithmus: Auswahl = Listenkopf (1. Element) Anschließend
	 * Listenkopf löschen, neue Seite (newPte) an Liste anhängen
	 */
	private PageTableEntry fifoAlgorithm(PageTableEntry newPte) {
		PageTableEntry pte; // Auswahl

		pte = (PageTableEntry) pteRAMlist.getFirst();
		os.testOut("Prozess " + pid + ": FIFO-Algorithmus hat pte ausgewählt: "
				+ pte.virtPageNum);
		pteRAMlist.removeFirst();
		pteRAMlist.add(newPte);
		return pte;
	}

	/**
	 * CLOCK-Algorithmus (Second-Chance): Nächstes Listenelement, ausgehend vom
	 * aktuellen Index, mit Referenced-Bit = 0 (false) auswählen Sonst R-Bit auf
	 * 0 setzen und nächstes Element in der pteRAMlist untersuchen. Anschließend
	 * die ausgewählte Seite durch die neue Seite (newPte) am selben Listenplatz
	 * ersetzen
	 */
	private PageTableEntry clockAlgorithm(PageTableEntry newPte) {
		PageTableEntry pte; // Aktuell untersuchter Seitentabelleneintrag

		// Immer ab altem "Uhrzeigerstand" weitersuchen
		pte = (PageTableEntry) pteRAMlist.get(pteRAMlistIndex);

		// Suche den nächsten Eintrag mit referenced == false (R-Bit = 0)
		while (pte.referenced == true) {
			// Seite wurde referenziert, also nicht auswählen, sondern R-Bit
			// zurücksetzen
			os.testOut("Prozess " + pid + ": CLOCK-Algorithmus! --- pte.vpn: "
					+ pte.virtPageNum + " ref: " + pte.referenced);
			pte.referenced = false;
			incrementPteRAMlistIndex();
			pte = (PageTableEntry) pteRAMlist.get(pteRAMlistIndex);
		}

		// Seite ausgewählt! (--> pteRAMlistIndex)
		// Alte Seite gegen neue in pteRAMlist austauschen
		pteRAMlist.remove(pteRAMlistIndex);
		pteRAMlist.add(pteRAMlistIndex, newPte);
		// Index auf Nachfolger setzen
		incrementPteRAMlistIndex();
		os.testOut("Prozess " + pid
				+ ": CLOCK-Algorithmus hat pte ausgewählt: " + pte.virtPageNum
				+ "  Neuer pteRAMlistIndex ist " + pteRAMlistIndex);

		return pte;
	}

	/**
	 * RANDOM-Algorithmus: Zufällige Auswahl 
	 */
	private PageTableEntry randomAlgorithm(PageTableEntry newPte) {
		// ToDo BSP
	}
	
	// ----------------------- Hilfsmethode --------------------------------
	private void incrementPteRAMlistIndex() {
		// ramPteIndex zirkular hochzählen zwischen 0 .. Listengröße-1
		pteRAMlistIndex = (pteRAMlistIndex + 1) % pteRAMlist.size();
	}

}
