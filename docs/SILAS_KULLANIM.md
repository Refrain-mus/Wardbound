# Silas Veyr — kullanım / davranış notları

- Boss değildir; Overworld için tekil, dünya-kayıtlı bir kişidir.
- Aynı anda ikinci bir doğal Silas üretilemez. Uzun süre görülmezse aynı kimlik başka uygun bir noktaya yolculuk etmiş gibi taşınabilir.
- Doğal karşılaşma herhangi bir Master hikâyesinin IV. bölümünden itibaren açılır.
- Master III civarında Silas henüz görünmeden anonim **TRACE** fragment'ları nadiren ortaya çıkmaya başlayabilir.
- Spawn kontrolü kasıtlı olarak seyrektir; güvenli zemin ve uzun vadeli garanti kontrolü kullanılır.

## Davranış

- Kamp çevresinde doğal roaming yoktur.
- Oturma, kalkma, çevreye bakma, ağırlık değiştirme, Nhal-Sûl'ü kontrol etme ve yorgunluk hareketleri vardır.
- Kendisine saldıran moblara karşılık verir; oyuncuyu veya çevredeki her canlıyı otomatik hedeflemez.
- Dövüşte kılıcı çeker, darbe frame'inde hasar verir ve savaş bitince kına koyar.
- Master temalı ileri konuşmalarda fiziksel reaksiyon kullanır: Gambler → kılıç kontrolü, Curator → bakışı başka noktaya kaydırma, Notary → ağır/yorgun duruş.

## Konuşma

- Sağ tık: mevcut progression aşamasındaki havuzdan bir sonraki satır.
- Eğilerek sağ tık: mevcut aşamada en son söylenen satırı tekrar eder.
- Chat spam yoktur; özel `silas_dialogue.png` penceresi kullanılır.
- Aşamalar birbirine sızmaz. İleri lore erken söylenmez.
- Stage V daha küçük havuz ve daha uzun konuşma cooldown'u kullanır; Silas artık bilgi dağıtan bir NPC değil, tanıktır.

## Progression

0. TRACE — Master III, yalnız anonim dünya fragment'ları.
1. WAYFARER — Master IV, Silas görünür; yol/Nhal-Sûl/eski şampiyonluk.
2. THREE COMPANIONS — Master V, Sera ve İlen, başarıların bedeli.
3. THE HANDS BEHIND IT — Master VI, Gambler/Curator/Notary isimleri ve rolleri.
4. UNCONCLUDED — Master VII veya ilk Master yenilgisi, Silas'ın korkusu ve tamamlanmayan ölüme dair ipuçları.
5. WITNESS — iki Master yenilgisi, kısa ve tanıklık ağırlıklı konuşmalar.

## UNCONCLUDED ölüm sekansı

Silas teknik olarak kalıcı biçimde ölemez. Fatal hit:

- normal death/despawn yerine yaklaşık 6 saniyelik `UNCONCLUDED` state başlatır;
- Silas diz çöker/çöker, navigasyon ve savaş kapanır;
- staged vanilla sound katmanları + ash/reverse-portal partikülleri çalışır;
- yakındaki oyuncu ilk kez görüyorsa `OBSERVATION // HIS DEATH DID NOT COMPLETE` kaydı açılır;
- sekans sonunda yaklaşık %30 canla geri döner ve yorgun recovery durumuna geçer;
- Nhal-Sûl hiçbir zaman death loot olmaz;
- sekans sonrası kısa süre içinde konuşulursa tek seferlik “Bakmayı bırak.” cevabı verir.

## World note / Witness Ledger

`silas_fragment` fiziksel not item'ı beş belge ailesinden biri olarak gelir:

- Road Fragment
- Archive Fragment
- Docket / Article
- Table Remnant
- Private Margin

Her aile ayrı document texture kullanır. Fragment'lar yalnız progression izin verdiğinde okunabilir; başka oyuncudan ileri seviye bir not alınsa bile metin erkenden açılmaz.

Uygun ve henüz okunmamış fragment'lar, başarıyla çözülmüş Wardbound chest'lerinde düşük ihtimalle bulunabilir. Kötü RNG'nin hikâyeyi sonsuza dek saklamaması için kısa pity sayacı vardır.

Okunan fragment kalıcı olarak Witness Ledger → **UNRESOLVED TESTIMONIES** bölümüne kopyalanır. Bölüm başlığı progression ile `UNKNOWN WAYFARER → THE FAILED CHAMPION → SILAS VEYR → SILAS VEYR // UNCONCLUDED → SILAS VEYR // LAST WITNESS` şeklinde evrilir.

## Komutlar

- `/wardbound silas status`
- `/wardbound silas summon`
- `/wardbound silas pose sit|stand|shift|look|sword|weary`

## Config / müzik

- `config/wardbound-silas.toml`
- `wardbound:silas_presence`
- `wardbound:silas_testimony`

## 12/12 testimony reward and companion call

On iki fiziksel fragment tamamlandığında herhangi bir fragment eldeyken eğilerek sağ tıklamak seti `The Unconcluded Testimony` halinde birleştirir. Daha önce okunmuş fakat kaybedilmiş fragmentlar, set tamamlanana kadar loot havuzunda yeniden görünebilir.

Bir Master'ı yenmek gerekmez. Ödül teslimi için Silas progression'ın `HANDS` aşamasına ulaşması yeterlidir; pratikte ilgili ileri belgeler nedeniyle Curator / Notary hikâyelerinde chapter 6 seviyesine kadar ilerlemek gerekir, fakat boss kill şartı yoktur.

Birleşik tanıklık Silas'a verildiğinde `The Worn Scabbard Ring` alınır. Bu item kopya NPC summon etmez; dünyadaki tek Silas kimliğini geçici olarak oyuncunun yanına taşır.

Varsayılan çağrı süresi 90 saniye, cooldown 12 dakikadır. `wardbound-silas.toml` içinden `companionSeconds` ve `callCooldownSeconds` değiştirilebilir.

Normal Silas combat participant değildir: hedef almaz, hasar almaz ve mobların onu hedef olarak seçmesi iptal edilir. Ring çağrısı sırasında bu kurallar geçici olarak açılır; Silas hostile mobları avlar, oyuncuyu takip eder ve bazı mobların aggro'sunu üstüne alabilir. Oyuncuları hedeflemez.

Master encounter'larında Ring artık cevap verir. Silas ilk kez bir Master'ın önüne gönüllü olarak döndüğünde özel korku-aşımı sahnesi ve Master'a özgü ilk karşılaşma replikleri çalışır. Aktif Master encounter sırasında companion süresi eksilmez; fight bitince kalan normal süre devam eder.
