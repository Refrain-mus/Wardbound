# Ashen Curator — Kül Arşivi

Bu paket Wardbound 39.4.0 **kaynak projesidir**; hazır mod JAR'ı değildir. Minecraft 1.20.1, Forge 47.4.20 ve Java 17 içindir. Kaynak klasöründe `gradlew.bat build` çalıştırın. GeckoLib 4.8.4, PlayerAnimator 1.0.2-rc1+1.20 ve mevcut Lodestone bağımlılığı korunmuştur. Sunucu ve istemciler aynı yeni sürümü kullanmalı: ağ protokolü 51.

## Karşılaşma

Curator sabit merkezli, yüzen sayfaları ve göğsünde açılan kitabıyla bir arşiv bekçisidir. Dört köşedeki arşiv bölmelerinin hangilerinin korunacağını oyuncular belirler. Blok yerleştirme, kırma veya kalıcı ateş yoktur.

| Okuma | Davranış ve cevap |
|---|---|
| Ash Shelves | Dikdörtgen kül rafları sırayla yanar. Önce ince fildişi sınır ve boş parşömen, sonra dolu köz ve kalın turuncu sınır görünür. Merkezdeki boş koridordan geçilebilir. |
| Errata | Yanan sayfalar oyuncuların ardışık konumlarını kaydeder; havadan o adreslere ilerler. Konum kilitlendikten sonra işaret değişmez. İşaretli daireyi terk edin. Son bölümde kül izi daha uzun sürer. |
| Cinder Folios | İçten dışa dört ayrı köz halkası. İç ve dış sınırlar gerçek hasar bandını gösterir; halkanın içi dolu bir hasar diski değildir. |
| Final Index | Dört mühür açılır. Bir mührün merkezinde 24 tick çömelmek onu korur; yakınında Archive Writ kullanmak açık mührü kırar. İki koruma veya iki kırma arşivlemeyi keser. |
| Incineration | Index kesilmezse bütün arşiv yanar. Korunmuş 7 x 7 bölmeler ve daha baştan aydınlatılan tek **son raf** güvenlidir. Son raf her Index'te değişir. Merkezde beklemek kurtarmaz. |
| Open Binding | İki mühürle arşivleme kesilince 110 tick, normal bir okuma hiç kimseye vuramazsa 70 tick karşı saldırı penceresi açılır. Göğüs kitabı açılır; hasar katsayısı 1.8 olur. Normal korumalı durumda katsayı .72'dir. Hit cap her iki durumda da geçerlidir. |

İlk bölüm **Accession**, saldırıları tek tek öğretir. %60 canda **Redaction** başlar: mevcut tehlikeler silinir, dört saniyelik geçiş oynar; sayfa izleri uzar, mühürler sınırlı iyileştirme yapar. %25 altında dış raf baskısı diğer okumalarla örtüşür. Bu ayrı bir Gambler tipi üçüncü el değildir.

Index iyileştirmesi okuma başına en fazla %2 bütçelidir; korunan/kırılan mühürler katkıyı azaltır, iki kırma iyileştirmeyi durdurur. En hızlı config'de bile Index tercihi için en az 120 tick, yakımdan önce ayrıca 45 tick uyarı vardır. Genel saldırı uyarıları 36 tick altına düşmez.

## Doğal ilerleme

Varsayılan koşullar: Curator hikâyesi 7, **Ash in the Margin** zinciri 5, `CurseEvolution.knownCount` ile en az bir keşfedilmiş lanet birleşimi. Bu kayıtlar eski dünyalardan doğrudan okunur; geçmiş ilerleme sıfırlanmaz. Sıradan kart revizyonu bu koşulun yerine geçmez.

Koşullar tamamlandığında **Archive Writ / Arşiv Fermanı** envantere gelir. Havadayken kullanmak geri dönen tanıklığı korur; çömelerek havada kullanmak yakar. Bu tercih kalıcıdır ve geçmişe yazılır. Bossun giriş hitabı seçimi tanır. Sonra fermanla düz arenanın merkezindeki zemine dokunun. Ferman tüketilmez; kaybolursa koşulları taşıyan oyuncuya yeniden verilir.

Witness Ledger ilerleme sayfasına Curator koşulları ve ödül hakkı eklenmiştir. İlk gerçek zaferde, kendi kanıtlarını tamamlamış ve hasar/mühür katkısı yapmış her aktif oyuncu **The Last Margin / Son Kenar Notu** alır. Elde tutulan bu egemenlik nesnesi bir ölümcül darbeyi bir kez arşivler; 30 saniye sonra en çok 8 hasarlık borç geri döner, normal koşullarda en az bir can bırakır. Kullanım hakkı nesneye değil oyuncuya aittir; nesne kopyalamak hak üretmez. Borç zamanı boyut değişimi ve yeniden bağlantıda korunur. Debug zaferleri ödül ve kanıt yazmaz.

## Model, VFX ve sunum

- Yeni GeckoLib modeli: parçalı kül cüppesi, boş başlık, katalog tacı, bağımsız kollar, göğüs kitabının iki kapağı, beş havada duran folyo.
- 12 animasyon: manifest, idle, idle_embers, sweep, rings, pages, index, open, transition, hurt, collapse, release.
- 16 özgün atlas motifi; her biri ayrı PNG'ye ayrıldı. Ek olarak kesin geometrik sınırlar için küçük beyaz `boundary.png` var. Kaynak atlas da pakette.
- Banka: `src/main/resources/assets/wardbound/textures/vfx/ashen_curator/`.
- Yeni katalog fişi boss arayüzü: iki bölümün başlığı, dört mühür durumu, okuma adı, Index sayacı/son raf ve açık kitap göstergesi.
- Texture katmanı summon, aura, hazırlık, hasar, geçiş, korunmuş alan, açık kitap, çöküş ve zaferde kullanılır. Lodestone kısa ömürlü dönen kül/köz hareketini tamamlar.
- PlayerAnimator: mühür etkileşiminde 0.7 saniyelik el yazısı jesti. Kamera ve hareket kontrolü oyuncuda kalır. Ayrı katman süre sonunda veya çıkışta kaldırılır.
- Reduced motion mevcut `accessibilityReduceMotion` ayarını kullanır; dekoratif dönüşler durur, moteler seyrekleşir, oyuncu jesti kapanır. Hasar işaretleri korunur.

## Ses ve kendi müziğiniz

**16 OGG dosyası özgün, sentetik çalışan taslaklardır; profesyonel kayıt veya bitmiş beste olarak sunulmuyor.** Kağıt sürtünmesi, bakır vuruşları, düşük arşiv uğultusu ve sözsüz fısıltı dokusu üretildi. Ses kaydı veya başka bir oyunun sesleri kullanılmadı. Her event ve altyazı kayıtlıdır.

Kendi parçanızı tam olarak şu dosyanın yerine koyun:

```text
src/main/resources/assets/wardbound/sounds/ashen_curator/boss_theme.ogg
```

Dosya gerçekten **Ogg Vorbis** olmalıdır; MP3 dosyasının uzantısını değiştirmek yeterli değildir. Sonra modu yeniden derleyin. Kaynak değiştirmeden resource pack ile kullanacaksanız paketin iç yolu:

```text
assets/wardbound/sounds/ashen_curator/boss_theme.ogg
```

Teslim edilen `Ashen-Curator-Music-Override.zip` bunun hazır şablonudur. İçindeki dosyayı değiştirip Minecraft resource pack olarak etkinleştirin. Örnek parça 48 saniyelik, döngüye uygun bir arşiv drone taslağıdır.

Müzik yalnız aktif katılımcıya çalar; bitince loop eder. Ölüm, collapse, mesafeden çıkış, boyut değişimi ve discard fade-out başlatır. Tam bağlantı kapanışında ses yöneticisi durdurulur. Yeni arşive geçerken önce eski fade tamamlanır. Gambler'ın müzik sınıfı ve ses dosyaları değiştirilmedi.

Diğer değiştirilebilir sesler `sounds/ashen_curator/` altında: manifestation, archive_opening, paper_movement, ash_sweep, seal_activation, seal_break, attack_charge, impact, phase_transition, ambient_presence, whispers, hurt, vulnerability, death, victory. `curator_*` event kimliklerini koruyarak aynı dosya adlarını değiştirin.

## Config ve komutlar

Ana Wardbound config'indeki `bosses.ashen_curator` bağımsızdır:

| Ayar | Varsayılan |
|---|---:|
| enabled / allow_repeat_victories | true / false |
| health / health_per_extra_player | 5400 / 1800 |
| damage_multiplier / attack_speed | 1 / 1 |
| maximum_incoming_hit | 160 |
| timeout_seconds | 1800 |
| required_story_chapter / required_chain_stage | 7 / 5 |
| required_curse_evolutions | 1 |
| dialogue / particle_detail | true / 2 |
| theme_enabled / theme_volume | true / 0.75 |
| theme_fade_in_ticks / theme_fade_out_ticks | 45 / 70 |

Savaş ve ses ayarları başlangıçta kopyalanır; değişiklikler yeni encounter'da uygulanır. `enabled=false` aktif Curator'ı da kapatır. Müzik ve particle detay değerleri sunucudan istemciye aktarılır. Minecraft Music ses seviyesi de müzik sesini etkiler.

```text
/wardbound curator debug kit
/wardbound curator debug summon
/wardbound curator debug summon_force
/wardbound curator status
/wardbound curator reload
```

Debug ve reload OP veya creative gerektirir. `summon` ilerlemeyi atlar, arena doğrular; `summon_force` arena kontrolünü de atlar, **zemin inşa etmez**. Normal arena düz 33 x 33, yukarıda dokuz blok boşluk ister. Oyuncu ölümünden sonra veya çıkışta tekrar denemek için mevcut fermanı kullanın.

## Multiplayer ve temizlik

Başlangıçta en fazla sekiz oyuncu alınır; can katılımcı sayısıyla ölçeklenir. Sonradan gelen izleyiciler hasar/ödül kazanmaz. Ölen, bağlantısı kesilen veya boyut değiştiren katılımcı çıkarılır; arena dışında beş saniye kalan da çıkarılır. Uzaklaşan oyuncunun barı ve müziği hemen kapanmaya başlar. Kimse kalmazsa beş saniyede encounter kapanır. Oyuncuların biri ayrılsa da kalanlar devam eder.

Tehlikeler bossun kendi listesinde en fazla 48 kayıtla tutulur; attack entity ve dünya değişikliği yoktur. Örtüşen alanlar aynı oyuncuya on tick'te birden fazla hasar çağrısı yapmaz. Çizimler canlı boss verisinden yapılır; discard ile izler kaybolur. Lodestone parçacıkları en fazla 18 tick yaşar; yeni üretim boss kaldırılınca durur. İstemci aynı anda en çok dört yakın Curator için VFX çizer.

Sunucu yeniden başlatması veya chunk'ın yükten çıkması yarım savaşı devam ettirmez. Yeniden yüklenen boss iptal edilir; ferman, seçimler, kazanılmış kanıt ve ödül hakkı korunur. Bu açık bir yeniden deneme politikasıdır.

## Doğrulama ve oyunda bakılacaklar

Yapılanlar: tüm Java kaynakları yerel önbellekteki Forge 47.4.20 resmi eşlemeleri, GeckoLib, Lodestone ve PlayerAnimator ile derlendi; 54 geometri/zaman/can invarianti geçti; 289 resource/model/animasyon/PNG alpha/altyazı/ses kontrolü geçti. Tüm 16 OGG dosyası FFmpeg ile çözüldü. Gambler'a özgü Java ve medya dosyalarının orijinal arşivle eşitliği kontrol edildi.

**Minecraft istemcisi veya gerçek dedicated server başlatılarak oyun testi yapılmadı. Tam Gradle build/reobfuscation ve oynanış dengesi doğrulanmış değil.** Bu oturumdaki Gradle wrapper kullanıcı önbelleği erişimi nedeniyle başlayamadı; Java API derleme denetimi alternatif olarak kullanıldı. Derleme kayıtları `docs/validation/` içindedir.

Oyunda özellikle şunları kontrol edin:

1. Survival'da kit ile tek oyuncu: işaret ile ilk hasar aralığı, melee/ranged erişimi, dört okumanın temposu.
2. Mühürleri iki koruma / iki kırma / karışık seçimle deneyin; Index'te yalnız son rafı kullanarak da kaçışın okunurluğuna bakın.
3. %60 geçişinde eski tehlikelerin silinmesi, %25 altında örtüşme, açık kitap hasarı ve ölüm sonrası dört saniyelik kapanış.
4. İki oyuncuda sağlık ölçeği, bir oyuncunun ölümü/disconnect'i, boyut değişimi, arena terk etme, dışarıdan katılanların ödül alamaması.
5. Düşük particle detail, reduced motion, farklı GUI ölçekleri ve varsa shader ile zemin işaretlerinin okunurluğu. Zemin düz olmalı.
6. Kaynak paketiyle kendi müziğiniz, 48 saniyeyi aşan loop, fade süreleri, kaynak yenileme ve art arda denemeler.
7. Gerçek progression'da koşulların korunması, ferman tercihi, ilk zafer ödülü ve Son Kenar Notu'nun tek kullanımı.

Model/animasyonların gerçek render görünümü, düşük FPS davranışı ve 4–7 dakikalık hedef savaş süresi oyun testinde ayarlanmalıdır. Ses taslakları son prodüksiyon müzik/ses bankasının yerine geçmez.
