package fr.nexaexchange.mobile.data

/**
 * Encodage Base58, alphabet Bitcoin — celui qu'utilise Solana pour les adresses.
 *
 * POURQUOI ON NE PREND PAS UNE BIBLIOTHEQUE
 * La documentation Solana Mobile importe `com.funkatronics.encoders.Base58`, mais cet
 * artefact arrive de facon transitive et son nom de module n'est pas documente. Ajouter
 * une dependance dont on ne sait pas nommer les coordonnees, pour trente lignes de code
 * archi-connues, c'est prendre un risque de compilation pour rien.
 *
 * VERIFICATION — faite AVANT d'ecrire ce fichier, le 13 septembre 2026.
 * J'ai ecrit cet algorithme exact en JavaScript dans le navigateur et compare sa sortie
 * a celle de web3.js sur cinq adresses reelles. Les cinq correspondent au caractere pres :
 *
 *   4w1F9Dzua91TQwgsTKdzxugYYWPTiyThHtJE32xvA9rK   portefeuille builder NEXA
 *   EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v   USDC
 *   11111111111111111111111111111111               System Program (32 octets nuls)
 *   So11111111111111111111111111111111111111112    wSOL
 *   EtrnLzgbS7nMMy5fbD42kXiUzGg8XQzJ972Xtk1cjWih   programme Phoenix
 *
 * Le troisieme cas est celui qui compte : 32 octets a zero. Une implementation qui oublie
 * les zeros de tete rend une chaine vide au lieu de trente-deux « 1 ». C'est l'erreur
 * classique, et elle ne se voit sur aucune adresse ordinaire.
 */
object Base58 {

    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        // Chaque octet nul de tete devient un « 1 ». Il faut les compter a part :
        // l'arithmetique qui suit les ferait disparaitre.
        var zeros = 0
        while (zeros < bytes.size && bytes[zeros].toInt() == 0) zeros++

        // log(256)/log(58) vaut 1,365... ; 138/100 majore, +1 pour l'arrondi.
        val size = (bytes.size - zeros) * 138 / 100 + 1
        val buffer = ByteArray(size)
        var length = 0

        for (i in zeros until bytes.size) {
            var carry = bytes[i].toInt() and 0xFF
            var j = 0
            var k = size - 1
            while ((carry != 0 || j < length) && k >= 0) {
                carry += 256 * (buffer[k].toInt() and 0xFF)
                buffer[k] = (carry % 58).toByte()
                carry /= 58
                k--
                j++
            }
            length = j
        }

        val sb = StringBuilder(zeros + length)
        repeat(zeros) { sb.append('1') }
        for (i in (size - length) until size) {
            sb.append(ALPHABET[buffer[i].toInt() and 0xFF])
        }
        return sb.toString()
    }

    /**
     * Decodage Base58 → octets. Necessaire pour fabriquer une transaction Solana :
     * une adresse y figure sous forme de 32 octets bruts, pas de texte.
     *
     * VERIFIE le 16 septembre 2026 contre web3.js sur cinq adresses, dont
     * `11111111111111111111111111111111` (32 octets nuls) qui piege les
     * implementations oubliant les zeros de tete. Cinq sur cinq identiques.
     */
    fun decode(s: String): ByteArray {
        if (s.isEmpty()) return ByteArray(0)

        var zeros = 0
        while (zeros < s.length && s[zeros] == '1') zeros++

        // log(58)/log(256) vaut 0,7325... ; 733/1000 majore, +1 pour l'arrondi.
        val size = (s.length - zeros) * 733 / 1000 + 1
        val buffer = ByteArray(size)
        var length = 0

        for (i in zeros until s.length) {
            var carry = ALPHABET.indexOf(s[i])
            require(carry >= 0) { "invalid base58 character: ${s[i]}" }
            var j = 0
            var k = size - 1
            while ((carry != 0 || j < length) && k >= 0) {
                carry += 58 * (buffer[k].toInt() and 0xFF)
                buffer[k] = (carry and 0xFF).toByte()
                carry = carry shr 8
                k--
                j++
            }
            length = j
        }

        val out = ByteArray(zeros + length)
        System.arraycopy(buffer, size - length, out, zeros, length)
        return out
    }

    /**
     * L'entree est-elle une adresse Solana valide, c'est-a-dire exactement 32 octets ?
     *
     * Ajoute le 17 septembre 2026 pendant la revue de securite. [decode] rendait
     * fidelement les octets de ce qu'on lui donnait, y compris d'une chaine tronquee ou
     * d'un champ JSON inattendu : la transaction construite ensuite etait mal formee, la
     * simulation echouait, et l'ecran disait « prix de liquidation indisponible » sans
     * que rien n'indique que la faute venait d'une adresse de 31 octets. On verifie donc
     * a l'entree, la ou l'information existe encore.
     */
    fun isValidAddress(s: String): Boolean =
        s.length in 32..44 && runCatching { decode(s).size == 32 }.getOrDefault(false)

    /** Adresse abregee pour l'affichage : `4w1F…A9rK`. */
    fun shorten(address: String, head: Int = 4, tail: Int = 4): String =
        if (address.length <= head + tail + 1) address
        else address.take(head) + "…" + address.takeLast(tail)
}
