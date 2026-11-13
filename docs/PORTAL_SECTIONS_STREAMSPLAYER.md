# StreamsPlayer for Android - sections for the shared media exchange portal

Handed over for the portal source in FastMediaSorter Android (`docs/media-exchange-portal/`), which publishes the
portal. Per `DOC-EXTERNAL-QUALITY` section 9 the portal is one corpus with several writers and one publisher: the
pages below already exist in the portal (`broadcast-receive`, `broadcast-start`, `watch-exchange`, owned by
FastMediaSorter Android and edited by every program), and each block here is the section StreamsPlayer for Android
adds to the page of that name, in English, Russian and Ukrainian at once. This repository never edits that source
(`CLAUDE.md` section 1); the blocks are applied there through that repository's process and its gates, and
the picture of the page stays the portal's. The English block is the original; the Russian and Ukrainian blocks
follow it sentence by sentence.

The words are the family's dictionary (`DEVICE-EXCHANGE` item CW): exchange server, relay through the server, via
Google Drive, cast to your own device, broadcast (what a person finds and plays) and broadcasting (what the sender
does). The product is named StreamsPlayer, with "for Android" only where the sentence needs the platform.

Corrections the existing portal pages need from the same hand-over, with no change to what they say about other
programs: where a page lists the devices that play broadcasts, the Android row is "StreamsPlayer (an Android phone,
a Wear OS watch)" and the Windows row stays "STREAMS Player".

## Page `broadcast-receive` - section "StreamsPlayer"

### English

In StreamsPlayer, open the catalog menu and choose **Exchange server**. Sign in once with the connection string, the
QR code, a file or the server's address, and the list **Live broadcasts** shows what the other devices of the account
are broadcasting now. **Play** starts a broadcast; **Keep as channel** adds it to your channels.

- **Import live broadcast** in the catalog menu takes a link or a descriptor, a file or a QR code. A broadcast with
  a password asks for it before it plays.
- A cast to your own device asks first: **Incoming broadcast** offers **Play** and **Decline**. The switch **Play
  broadcasts sent from my devices without asking** is off until you turn it on.
- **Via Google Drive**, on the same screen, lists the broadcasts that your other devices announce through your own
  Google account, with no server and no typed address.
- A certificate that a server shows for the first time is asked about; a certificate that changed is refused.

### Русский

В StreamsPlayer откройте меню каталога и выберите **Сервер обмена**. Один раз войдите по строке подключения, QR-коду,
файлу или адресу сервера, и список **Прямые трансляции** покажет, что сейчас вещают другие устройства аккаунта.
**Воспроизвести** запускает трансляцию, **Сохранить как канал** добавляет её в ваши каналы.

- **Импорт трансляции** в меню каталога принимает ссылку или дескриптор, файл или QR-код. Трансляция с паролем
  спрашивает его до начала воспроизведения.
- Cast на своё устройство сначала спрашивает: окно **Входящая трансляция** предлагает **Воспроизвести** и
  **Отклонить**. Переключатель **Воспроизводить трансляции с моих устройств без подтверждения** выключен, пока вы сами
  его не включите.
- Карточка **Через Google Drive** на том же экране показывает трансляции, которые ваши другие устройства объявляют
  через ваш аккаунт Google, без сервера и без ввода адреса.
- О сертификате, который сервер показывает впервые, приложение спрашивает; изменившийся сертификат отклоняется.

### Українська

У StreamsPlayer відкрийте меню каталогу й виберіть **Сервер обміну**. Один раз увійдіть за рядком підключення,
QR-кодом, файлом або адресою сервера, і список **Прямі трансляції** покаже, що зараз мовлять інші пристрої акаунта.
**Відтворити** запускає трансляцію, **Зберегти як канал** додає її до ваших каналів.

- **Імпорт трансляції** в меню каталогу приймає посилання або дескриптор, файл чи QR-код. Трансляція з паролем питає
  його до початку відтворення.
- Cast на свій пристрій спершу питає: вікно **Вхідна трансляція** пропонує **Відтворити** і **Відхилити**. Перемикач
  **Відтворювати трансляції з моїх пристроїв без підтвердження** вимкнений, доки ви самі його не ввімкнете.
- Картка **Через Google Drive** на тому самому екрані показує трансляції, які ваші інші пристрої оголошують через ваш
  акаунт Google, без сервера й без введення адреси.
- Про сертифікат, який сервер показує вперше, застосунок питає; сертифікат, що змінився, відхиляється.

## Page `broadcast-start` - section "StreamsPlayer"

### English

In StreamsPlayer the phone broadcasts its microphone or its camera from **Broadcast Control**, and the watch
broadcasts its microphone from **Broadcast**. Listeners on your network reach the broadcast at the phone's address
with the link or the QR code.

- With an exchange server added on the **Exchange server** screen, every broadcast of the phone is announced there
  from its start, with no switch to turn on. **Broadcast Control** names the server and the state of the broadcast
  there; without a server it says the broadcast is reachable on this network only. The server card says that the
  server's operator can see that a broadcast runs while the relay through the server runs; removing the server keeps
  broadcasts on your network.
- **Announce via Google Drive** is off until you turn it on. It writes a small announcement file to the hidden app
  folder of your own Google account while the broadcast runs and removes it when the broadcast stops; the sound and
  the picture never go through Drive.
- A guest account publishes and announces nothing.
- The server card carries **Learn more**, which opens this page.

### Русский

В StreamsPlayer телефон вещает с микрофона или с камеры из экрана **Управление вещанием**, а часы - с микрофона из
раздела **Трансляция**. Слушатели в вашей сети подключаются по ссылке или QR-коду к адресу телефона.

- Когда на экране **Сервер обмена** добавлен сервер, каждое вещание телефона объявляется на нём с самого начала, и
  включать для этого ничего не нужно. **Управление вещанием** называет сервер и состояние вещания на нём; без сервера
  оно говорит, что вещание доступно только в этой сети. Карточка сервера говорит, что его оператор видит, что вещание
  идёт, пока идёт раздача потока через сервер; если удалить сервер, вещания остаются в вашей сети.
- Переключатель **Объявить через Google Drive** выключен, пока вы сами его не включите. Пока идёт вещание, он пишет
  небольшой файл объявления в скрытую папку приложения в вашем аккаунте Google и удаляет его, когда вещание
  заканчивается; звук и картинка через Drive не идут.
- Гостевой аккаунт ничего не публикует и не объявляет.
- На карточке сервера есть кнопка **Подробнее**, она открывает эту страницу.

### Українська

У StreamsPlayer телефон мовить з мікрофона або з камери з екрана **Управління мовленням**, а годинник - з мікрофона з
розділу **Трансляція**. Слухачі у вашій мережі підключаються за посиланням або QR-кодом до адреси телефона.

- Коли на екрані **Сервер обміну** додано сервер, кожне мовлення телефона оголошується на ньому від самого початку, і
  вмикати для цього нічого не треба. **Управління мовленням** називає сервер і стан мовлення на ньому; без сервера воно
  каже, що мовлення доступне лише в цій мережі. Картка сервера каже, що його оператор бачить, що мовлення триває, поки
  триває роздача потоку через сервер; якщо видалити сервер, мовлення лишаються у вашій мережі.
- Перемикач **Оголосити через Google Drive** вимкнений, доки ви самі його не ввімкнете. Поки триває мовлення, він
  пише невеликий файл оголошення в приховану теку застосунку у вашому акаунті Google і видаляє його, коли мовлення
  закінчується; звук і картинка через Drive не йдуть.
- Гостьовий акаунт нічого не публікує й не оголошує.
- На картці сервера є кнопка **Докладніше**, вона відкриває цю сторінку.

## Page `watch-exchange` - section "StreamsPlayer"

### English

In StreamsPlayer the phone connects the watch to the account: on the **Exchange server** screen, **Connect watch**
hands the watch a one-time pairing code, and the watch keeps its own encrypted sign-in; no account password is sent to
it.

- The watch broadcasts sound only. Every broadcast of a connected watch is announced on the exchange server from its
  start, with no switch, and its **Broadcast** screen says whether the broadcast is on the server; the watch carries at
  most four listeners across the local network, the relay through the server and the tunnel together.
- The connection to the server stays open while a broadcast runs, and otherwise only while the broadcast or the audio
  player screen is visible.
- **Help on the phone** on that screen opens this page on the phone, in the phone's language.
- Signing out on the phone removes the watch from the account.

### Русский

В StreamsPlayer телефон подключает часы к аккаунту: на экране **Сервер обмена** кнопка **Подключить часы** передаёт
часам одноразовый код сопряжения, а часы хранят свой зашифрованный вход; пароль аккаунта им не передаётся.

- Часы вещают только звук. Каждое вещание подключённых часов с самого начала объявляется на сервере обмена, без
  переключателя, а их экран **Трансляция** показывает, есть ли вещание на сервере; слушателей у часов не больше четырёх
  на локальную сеть, раздачу потока через сервер и туннель вместе.
- Соединение с сервером остаётся открытым, пока идёт вещание, а в остальное время - только пока виден экран вещания или
  аудиоплеера.
- Кнопка **Помощь на телефоне** на этом экране открывает эту страницу на телефоне, на языке телефона.
- Выход из аккаунта на телефоне удаляет часы из аккаунта.

### Українська

У StreamsPlayer телефон підключає годинник до акаунта: на екрані **Сервер обміну** кнопка **Підключити годинник**
передає годиннику одноразовий код сполучення, а годинник зберігає свій зашифрований вхід; пароль акаунта йому не
передається.

- Годинник мовить лише звук. Кожне мовлення підключеного годинника від самого початку оголошується на сервері обміну,
  без перемикача, а його екран **Трансляція** показує, чи є мовлення на сервері; слухачів у годинника не більше чотирьох
  на локальну мережу, роздачу потоку через сервер і тунель разом.
- З'єднання із сервером лишається відкритим, поки триває мовлення, а в решту часу - лише поки видно екран мовлення або
  аудіоплеєра.
- Кнопка **Допомога на телефоні** на цьому екрані відкриває цю сторінку на телефоні, мовою телефона.
- Вихід з акаунта на телефоні видаляє годинник з акаунта.
