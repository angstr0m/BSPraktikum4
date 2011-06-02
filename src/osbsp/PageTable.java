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
	private int pteRAMlistIndex; // Uhrzeiger f�r Clock-Algorithmus

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
		index = 0; // Index auf das n�chste Element
		pteRAMlist = new LinkedList<PageTableEntry>();
		pteRAMlistIndex = 0;
	}

	/**
	 * R�ckgabe: Seitentabelleneintrag pte (PageTableEntry) f�r die �bergebene
	 * virtuelle Seitennummer (VPN = Virtual Page Number) oder null
	 */
	public PageTableEntry getPte(int vpn) {

		if ((vpn < 0) || (vpn >= index)) {
			// os.testOut("PageTable.getPte() in Prozess "+pid+": R�ckgabe null,
			// da Seite "+vpn+" noch nicht existiert!");
			return null;
		} else {
			return pageTableArray[vpn];
		}
	}

	/**
	 * Einen Eintrag (PageTableEntry) an die Seitentabelle anh�ngen. Die
	 * Seitentabelle darf nicht sortiert werden!
	 */
	public void addEntry(PageTableEntry pte) {
		if (index < PAGETABLE_MAX_SIZE) {
			pageTableArray[index] = pte;
			// os.testOut("PageTable in Prozess "+pid+": Eintrag f�r Index
			// "+index+" erfolgreich erzeugt!");
			index++;
		} else {
			System.out.println("--------- Schwerer Fehler in Prozess " + pid
					+ ": PageTable overflow!!!!");
		}
	}

	/**
	 * R�ckgabe: Aktuelle Gr��e der Seitentabelle.
	 */
	public int getSize() {
		return index;
	}

	/**
	 * Pte in pteRAMlist eintragen, wenn sich die Zahl der RAM-Seiten des
	 * Prozesses erh�ht hat.
	 */
	public void pteRAMlistInsert(PageTableEntry pte) {
		os.testOut("pteRAMlistInsert in Prozess " + pid + ": pte mit vpn "
				+ pte.virtPageNum + " angef�gt!");
		pteRAMlist.add(pte);
	}

	/**
	 * Eine Seite, die sich im RAM befindet, anhand der pteRAMlist ausw�hlen und
	 * zur�ckgeben
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
	 * FIFO-Algorithmus: Auswahl = Listenkopf (1. Element) Anschlie�end
	 * Listenkopf l�schen, neue Seite (newPte) an Liste anh�ngen
	 */
	private PageTableEntry fifoAlgorithm(PageTableEntry newPte) {
		PageTableEntry pte; // Auswahl

		pte = (PageTableEntry) pteRAMlist.getFirst();
		os.testOut("Prozess " + pid + ": FIFO-Algorithmus hat pte ausgew�hlt: "
				+ pte.virtPageNum);
		pteRAMlist.removeFirst();
		pteRAMlist.add(newPte);
		return pte;
	}

	/**
	 * CLOCK-Algorithmus (Second-Chance): N�chstes Listenelement, ausgehend vom
	 * aktuellen Index, mit Referenced-Bit = 0 (false) ausw�hlen Sonst R-Bit auf
	 * 0 setzen und n�chstes Element in der pteRAMlist untersuchen. Anschlie�end
	 * die ausgew�hlte Seite durch die neue Seite (newPte) am selben Listenplatz
	 * ersetzen
	 */
	private PageTableEntry clockAlgorithm(PageTableEntry newPte) {
		PageTableEntry pte; // Aktuell untersuchter Seitentabelleneintrag

		// Immer ab altem "Uhrzeigerstand" weitersuchen
		pte = (PageTableEntry) pteRAMlist.get(pteRAMlistIndex);

		// Suche den n�chsten Eintrag mit referenced == false (R-Bit = 0)
		while (pte.referenced == true) {
			// Seite wurde referenziert, also nicht ausw�hlen, sondern R-Bit
			// zur�cksetzen
			os.testOut("Prozess " + pid + ": CLOCK-Algorithmus! --- pte.vpn: "
					+ pte.virtPageNum + " ref: " + pte.referenced);
			pte.referenced = false;
			incrementPteRAMlistIndex();
			pte = (PageTableEntry) pteRAMlist.get(pteRAMlistIndex);
		}

		// Seite ausgew�hlt! (--> pteRAMlistIndex)
		// Alte Seite gegen neue in pteRAMlist austauschen
		pteRAMlist.remove(pteRAMlistIndex);
		pteRAMlist.add(pteRAMlistIndex, newPte);
		// Index auf Nachfolger setzen
		incrementPteRAMlistIndex();
		os.testOut("Prozess " + pid
				+ ": CLOCK-Algorithmus hat pte ausgew�hlt: " + pte.virtPageNum
				+ "  Neuer pteRAMlistIndex ist " + pteRAMlistIndex);

		return pte;
	}

	/**
	 * RANDOM-Algorithmus: Zuf�llige Auswahl 
	 */
	private PageTableEntry randomAlgorithm(PageTableEntry newPte) {
		PageTableEntry pte; // Aktuell untersuchter Seitentabelleneintrag
                


		// Seite ausgew�hlt! (--> pteRAMlistIndex)
		// Alte Seite gegen neue in pteRAMlist austauschen
                Random randomPageNum = new Random();
                int randomIndex = randomPageNum.nextInt(pteRAMlist.size());
                pte = (PageTableEntry) pteRAMlist.get(randomIndex);
		pteRAMlist.remove(randomIndex);
		pteRAMlist.add(randomIndex, newPte);

		os.testOut("Prozess " + pid
				+ ": Rand-Algorithmus hat pte ausgew�hlt: " + pte.virtPageNum
				+ "  Neuer pteRAMlistIndex ist " + pteRAMlistIndex);

		return pte;
	}
	
	// ----------------------- Hilfsmethode --------------------------------
	private void incrementPteRAMlistIndex() {
		// ramPteIndex zirkular hochz�hlen zwischen 0 .. Listengr��e-1
		pteRAMlistIndex = (pteRAMlistIndex + 1) % pteRAMlist.size();
	}

}
