# Wardbound v43 — End-Endgame Masters

Kaynak proje paketi. Minecraft 1.20.1 / Forge 47.4.20 / GeckoLib 4.8.4 / Java 17.
Mod sürümü 39.3.0, ağ protokolü 46. Sunucu ve istemci aynı sürümde olmalıdır.

## Değişiklikler
- Cthulhu Idol gözünün yatay hareketi ±0,055, dikey hareketi ±0,022 model birimine indirildi. Yatay döngü 30 saniye, dikey döngü 45 saniye; göz bebeği genişliği yalnızca %4 değişir. Invoke sırasındaki göz hareketleri de azaltıldı.
- Pale Gambler 1,65 kat büyütüldü. Gövde çarpışma kutusu da büyütüldü. Eller yüzü kapatmadan aşağıya/açığa uzanır.
- Boss barı yalnızca The Pale Gambler adını taşır; altın/mor özel çerçevesi kodla çizilir. Yeni bir bitmap bossbar dokusu kullanılmaz.
- Konuşmalar sohbet yerine Savant arayüzünde, bossun kendi başlığıyla gösterilir.
- Parçacıkların yanında sürekli geometrik saldırı işaretleri vardır. Düşük parçacık ayarı bunları kapatmaz. Emerald yeşili güvenli bölge; mor/altın yaklaşan saldırı; kırmızı merkez tehdidi ve vuruşları belirtir.
- Dönen büyü parçacıkları, ruh alevleri, elektrikli kesici çizgiler, evre ve ölüm patlamaları eklendi. Zemine blok yerleştirilmez veya blok kırılmaz.

## Güç ve ilerleme
Varsayılan etkili can: 16.000 tek kişi, 23.000 iki kişi, 30.000 üç kişi, 37.000 dört kişi.
Minecraft'ın 1.024 maksimum can niteliği sınırını aşmak için ayrı, kaydedilen bir boss can havuzu vardır. Normal zırh ve Forge hasar hesaplamasından çıkan gerçek hasar bu havuzdan düşülür. Boss barı bu gerçek havuzu gösterir; genel mob canı okuyan bazı üçüncü taraf göstergeler 1.024 tampon canı gösterebilir.
Zırh 18, zırh sertliği 10, vuruş başına gelen ham hasar üst sınırı 180. Varsayılan süre sınırı 30 dakika. Config ile değiştirilebilir.
Normal çağırma: Gambler hikâyesi 7, Black Table 5, en az 3 revize kart. Kazanmış oyuncu varsayılan olarak tekrar çağıramaz. Başka oyuncunun savaşına katılabilir; tekrar zafer hiçbir ek ödül vermez.
Eski sürümde kazanılmış zafer kaydı da geçerlidir. Daha önce verilmiş sürekli ek kart eli bonusu kaldırıldı.

## Sekiz saldırı
Can %67 ve %34 sınırlarında evre değişir. Açılış 4 saniye; evre geçişi 2,5 saniye. Normal hamlelerde varsayılan işaret süresi 1,7 / 1,5 / 1,3 saniyedir. Vuruşlardan sonra uzun boş bekleme kaldırıldı.
1. Yelpaze: 7 / 9 / 11 kesici hat. Evre başına 1 / 2 / 3 kaydırılmış dalga. Ham hasar 40 / 46 / 52.
2. Halkalar: beş dalga; son evrede dıştan içe. Zıplayarak veya halkayı geçerek kaçılır. Ham hasar 42 / 48 / 54.
3. İmzalı gölgeler: 2 / 3 / 4 saldırı; dalgalar arasında oyuncunun yeni konumu tekrar işaretlenir. Ham hasar 44 / 50 / 56.
4. Dönen kesiciler: karşılıklı iki hat, sekiz adım boyunca döner. Ham hasar 46 / 52 / 58.
5. Kafes: aralarında güvenli hücreler bulunan ızgara, sonraki vuruşta 2,5 blok kayar. Ham hasar 48 / 54 / 60.
6. Son hüküm: dört yeşil mühürden birinin içine girilmelidir. Dışarısı 80 / 88 / 96 ham hasar alır. Varsayılan hazırlık 3,8 saniye; hız artırılsa bile en az 3 saniyedir. Son evrede güvenli mühürler dönerek ikinci hüküm gelir.
7. Masadaki kartlar: arenaya 3-4 büyük, döndürülmüş oyun kartı alanı serilir. Yalnızca bu kartların içinde kalan oyuncular güvenlidir; dışarıda kalanlar maksimum canlarının %80’i ölçeğinde büyü hasarı ve kısa Wither alır. Kartların dış ve iç çerçeveleri ile suit çizgileri parçacık ayarından bağımsız geometrik telegraph olarak da gösterilir.
8. Zorunlu el: iki ayrı kart arayüzü açılır. Thirty Cut maksimum canın %30’unu alır. Coin's Edge sunucuda atılan gerçek %50 zarla ya tamamen ıskalar ya da maksimum canın %50’si ölçeğinde hasar verir. ESC/E ile kapanmaz; ekran başka bir GUI ile değiştirilirse yeniden açılır. Süre aşımı, boyut değiştirme veya bağlantıyı keserek kaçma girişimi server-side pending kayıtla korunur ve kötü sonuç (%50) tahsil edilir.
Her hamlenin ilk vuruşunda merkezin 3,1 blok çevresi de tehlikelidir. Çakışan saldırılar aynı oyuncuya aynı tikte daha küçük vuruşla koruma sağlamasın diye en yüksek hasar tek vuruşta uygulanır. İkinci evreden itibaren isabetler kısa Wither etkisi ekler. Oyuncunun normal savunmaları geçerlidir.
Kaçış zemininin düz, kuru, 16 blok yarıçaplı ve 7 blok yüksekliği boş olması gerekir. 18 blok dışına herkes çıkarsa 10 saniye sonra savaş kapanır. Test çağırması ödülsüzdür.

## Housebreaker's Ace
Oyuncu başına toplam iki kullanım. Eşya kopyaları, geri alma, ölüm veya kayıt yükleme bu sayacı yenilemez. İkinci başarılı kullanımda eldeki eşya tüketilir.
Normal kullanım: sonraki sıradan ward için +1 başlangıç hakkı.
Eğilerek kullanım: sonraki sıradan ward için +%35 ganimet, -1 başlangıç hakkı.
Bir seçim sıradayken ikinci seçim yapılamaz ve kullanım hakkı harcanmaz. Eşya ward açılmadan kaybolsa da sıradaki etki kayıtlı kalır. Kullanılmış seçim ward açılınca tüketilir; kaybedilirse iade edilmez. Mevcut tek-hak zorlamaları önceliğini korur.

## Config ve komutlar
`config/wardbound.json` içine `bosses.pale_gambler` bölümü eklenir. v42'nin aynı şema sürümündeki mevcut ayarları korunarak eksik boss bölümü otomatik eklenir. Eklenen örnek JSON yalnız boss bölümüdür; tek başına tüm config dosyasının yerine kullanılmamalıdır.
- `enabled`: bossu açar/kapatır; kapatılırsa aktif boss sonraki tikte kaldırılır.
- `allow_repeat_victories`: varsayılan false. Açılması tekrar öldürmeye ödül eklemez ve Ace haklarını sıfırlamaz.
- `health`, `health_per_extra_player`, `damage_multiplier`, `attack_speed`, `maximum_incoming_hit`, `timeout_seconds`: savaş ayarları.
- `required_story_chapter`, `required_chain_stage`, `required_revised_cards`: ilerleme koşulları.
- `dialogue`: konuşma arayüzü. `particle_detail`: 0 asgari / 1 normal / 2 süslü. Tehlike çizgileri korunur.
`/wardbound gambler reload`: OP yetkisiyle config'i tekrar okur. Sağlık, hız, hasar ve görsel ayrıntı ayarları yeni savaşlarda uygulanır; başlamış savaşın değerleri kaydedilir.
`/wardbound gambler summon`: OP için ödülsüz test savaşı.
`/wardbound gambler status`: ilerleme koşulları.
`/wardbound gambler invitation`: koşulları sağlayan oyuncuya kayıp davetiye.
`/wardbound gambler reclaim`: zafer kazanmış ve iki hakkını tüketmemiş oyuncuya kayıp Ace.
Gelecek Masterlar için ortak BossConfig kayıt sistemi hazırdır. Yeni bir Master eklendiğinde kendi ayar kaydı ve savaş davranışı bu sisteme bağlanır; mevcut olmayan başka bosslar bu pakette varmış gibi tanımlanmadı.

## Derleme ve doğrulama
`gradlew.bat build` ile derleyin; normal Forge için yeniden eşlenmiş JAR `build/libs` altında oluşur. Bu ZIP kaynak projedir, doğrudan mods klasörüne konulacak JAR değildir.
141 Java dosyası mevcut Minecraft/Forge/GeckoLib bağımlılıklarıyla derlendi: 0 hata. Önceden bulunan API uyarıları sürüyor.
4.924 kart kontrolü ve 870 yeni endgame kontrolü geçti: config sınırları ve kalıcılığı, büyük can havuzu, sekiz saldırı, güvenli alanlara ulaşılabilirlik, iki kullanım sınırı, oyuncuların kayıtlarının ayrılığı ve ağ paketleri.
Model/animasyon JSON bağlantıları, UV sınırları ve modelden üretilen el pozu önizlemesi kontrol edildi. Önizleme oyun ekran görüntüsü değildir.
Tam Gradle/reobfuscation veya Minecraft içinde shaderlı ve çok oyunculu oynanış testi burada yapılmadı. Bu yüzden gerçek savaş dengesi ve efektlerin oyun içindeki görünüşü henüz doğrulanmış sayılmaz.
