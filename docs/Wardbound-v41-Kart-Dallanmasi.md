# Wardbound v41 — Kart evriminde iki yol

Kaynak proje sürümü: 39.1.9. Bu paket 18 evrilebilir karta ikinci bir seçim yolu ekler. Önceki katmanlı Cthulhu İdol modeli, göz ve kanat animasyonları da dahildir. Master boss'ları henüz dahil değildir; ayrı tasarım belgesinde ele alındı.

## Nasıl çalışıyor?

İlk imza eski davranışı korur. Evrilmiş bir kartı seçtiğinde iki yolun etkilerini karşılaştıran ekran açılır. Bir yol imzalanana kadar kart alınmaz; geri dönerek başka kart seçebilirsin.

**Original Clause**, kartın mevcut evrimidir. İkinci yol farklı bir avantaj ve karşılık getirir. Her yeni evrilmiş kopyayı imzalarken yeniden seçim yapılabilir. Mevcut 2., 4. ve 8. imzadaki evrim eşikleri korunmuştur.

Seçim oyuncu ve kart bazında kaydedilir. Eski kayıtlarda seçilmemiş kartlar özgün davranışını sürdürür. Grimoire'ın kart sayfası seçtiğin yolun gerçek etkisini gösterir. Küçük ekranlarda uzun açıklamalar kaydırılabilir.

## İkinci yollar

İkinci yol aksini belirtmediği sürece mevcut kullanım sayısını ve toplam süreyi korur.

| Kart | Yeni yol | Değişen davranış |
|---|---|---|
| Loaded Dice | Quiet Odds | Kullanım başına +%12 ganimet; başlangıç canı eksilmez, mekanizma hızlanmaz. |
| Mercy's Due | Time to Repent | Ek can yerine +6 saniye; ilk hata affı ve ganimet bedeli korunur. |
| Ember Count | Beacon Brand | Normal yanma 2 saniyeye iner, hedef 8 saniye görünür olur. Ashen Brand kombinasyonunun 8 saniyelik yanması korunur. |
| Ore Whisper | Close Reading | Arama yarıçapı 4 bloğa iner; her taramada kapsama giren bütün madenler işaretlenir. |
| Borrowed Momentum | Measured Pace | Speed II yerine Speed I; bitişteki Slowness borcu kalkar. |
| Hunter's Dividend | Public Bounty | Öldürme başına 4 deneyim, yiyecek ödemesi yok; her ödeme oyuncuyu 5 saniye görünür yapar. |
| Bellglass Sight | Night Reader | Düşük ışık Night Vision'ı yeniler, aydınlıkta doğal olarak biter; karttan gelen görünürlük ve gündüz sersemlemesi kalkar. |
| Coal Kiss | Clinging Ash | Vuruşlar Weakness yerine aynı süreli Slowness I verir. |
| Hearthmark | Shelter at Supper | Yemekler Regeneration yerine 20 saniye Absorption I verir; son Hunger borcu korunur. |
| Salt Circle | Pale Pursuit | Ölümsüz öldürmek Absorption yerine 12 saniye Speed I verir. |
| Black Compass | Closing Circle | Algılama 12 bloğa iner; yakındaki düşmanlar görünür olur ve yavaşlar. |
| Iron Echo | Iron Anchor | Güçlü geri itme yerine hafif geri itme ve 4 saniye Slowness I. |
| Grave Ration | Living Ration | Yiyecek/deneyim yerine düşman öldürünce yarım kalp iyileşme. |
| Pocket Eclipse | Still Shadow | Karanlık yerine yerde çömelip hareketsiz durmak görünmezliği yeniler; ışıkta da çalışır. |
| Pilgrim's Luck | Counted Steps | Rastgele ödeme yerine evrim seviyesine göre her 5/4/3 blokta bir deneyim. Ore Whisper kombinasyonu aralığı bir azaltır. |
| Vein Drinker | Blood Reserve | İyileşme yerine 10 saniye Absorption I; Red Communion bunu Absorption II yapar. |
| Quicksilver Prayer | Miner's Psalm | Speed I + Haste I yerine Haste II; bitişteki Weakness borcu korunur. |
| Ferryman's Ledger | Deep Crossing | En az 2 kalplik darbede %35 azaltma yerine, en az 4 kalplik darbede %50 azaltma. Düşme hasarı kapsam dışı; son kullanım bonusu korunur. |

## Kullanım ve doğrulama

ZIP kaynak projedir, derlenmiş JAR değildir. Kaynak projeyi Forge geliştirme düzeninde normal şekilde derleyin. Ağ mesajına kart yolu eklendiği için istemci ve sunucunun ikisi de bu sürümü kullanmalıdır; protokol 42'den 43'e yükseltildi.

126 Java kaynak dosyası, önbellekteki Forge 1.20.1 / 47.4.20 ve GeckoLib 4.8.4 geliştirme bağımlılıklarıyla hatasız derlendi. Mevcut kullanımdan kaldırılmış API uyarıları devam ediyor. Bu doğrulama tam Gradle yeniden eşleme/JAR paketleme çalıştırması değildir.

Gerçek kart ve kayıt sınıflarıyla çalışan denetim programı; 18 kartın yol seçeneklerini, geçersiz seçimleri, paket kodlama/çözmesini, evrim eşiklerini, eski kayıt davranışını, kaydetme/yüklemeyi, oyuncu ayrımını, iki mühür kartının etkilerini ve kullanım tükenmesini denetledi. 4924 koşul doğrulandı. Bu sayı 4924 ayrı oyun senaryosu anlamına gelmez.

Minecraft içinde arayüz ve savaş/dünya kartı etkileri oynanarak test edilmedi. Özellikle aynı anda birden çok kart, diğer modların iksir etkileri ve çok oyunculu gerçek oturum son oyun testinin kapsamındadır.

Master endgame'in karşılaşma şartları, üç farklı boss kimliği, ilişki ağı ve uygulama sırası **Wardbound-Master-Endgame-Tasarimi.md** belgesindedir.
