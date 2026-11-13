package com.sza.streamsplayer.data.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * S0032 scope 5: the account screen shows the honest text of DEVICE-EXCHANGE 27 item DE, in its words, not reworded
 * and not shortened, in every locale this product ships.
 */
class ExchangeHonestTextTest {

    private fun honestText(dir: String): String {
        val file = File("src/main/res/$dir/strings.xml")
        assertTrue("${file.path} must exist", file.isFile)
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        val node = (0 until nodes.length).map { nodes.item(it) }
            .single { it.attributes.getNamedItem("name").nodeValue == NAME }
        return node.textContent.replace("\\'", "'")
    }

    @Test
    fun `the honest text is the contract's in English, Russian and Ukrainian`() {
        assertEquals(EN, honestText("values"))
        assertEquals(RU, honestText("values-ru"))
        assertEquals(UK, honestText("values-uk"))
    }

    @Test
    fun `the account screen shows the honest text`() {
        val layout = File("src/main/res/layout/activity_exchange.xml").readText()
        assertTrue("activity_exchange.xml must show @string/$NAME", layout.contains("@string/$NAME"))
    }

    private companion object {
        const val NAME = "exchange_server_honest_text"

        const val EN = "The exchange server connects your devices, so it knows the names of your account, devices, " +
            "folders and broadcasts, sees which devices are online, and how much is sent and when. It cannot open " +
            "what goes through it: folders and their files, broadcasts and the clipboard are encrypted on your " +
            "device and opened only on your other devices or by the people you gave access to. To look inside, " +
            "whoever runs the server would have to add a device of their own to your account, and it would appear " +
            "in your list of devices."

        const val RU = "Сервер обмена соединяет ваши устройства, поэтому знает имена вашего аккаунта, устройств, " +
            "папок и трансляций, видит, какие устройства в сети, и сколько и когда передаётся. Открыть то, что идёт " +
            "через него, он не может: папки и их файлы, трансляции и буфер шифруются на вашем устройстве и " +
            "открываются только на других ваших устройствах или у тех, кому вы дали доступ. Чтобы заглянуть " +
            "внутрь, тому, кто управляет сервером, пришлось бы добавить в ваш аккаунт своё устройство, и оно " +
            "появилось бы в списке ваших устройств."

        const val UK = "Сервер обміну з'єднує ваші пристрої, тому знає імена вашого акаунта, пристроїв, папок і " +
            "трансляцій, бачить, які пристрої в мережі, і скільки й коли передається. Відкрити те, що йде через " +
            "нього, він не може: папки та їхні файли, трансляції й буфер шифруються на вашому пристрої та " +
            "відкриваються лише на інших ваших пристроях або в тих, кому ви дали доступ. Щоб зазирнути " +
            "всередину, тому, хто керує сервером, довелося б додати до вашого акаунта свій пристрій, і він " +
            "з'явився б у списку ваших пристроїв."
    }
}
