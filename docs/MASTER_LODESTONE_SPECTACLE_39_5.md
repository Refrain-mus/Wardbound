# Wardbound — Master Lodestone Spectacle 39.5.0

Temel alınan paket: `Wardbound-Master-Epic-3D-VFX-Pass.zip`. Bu paketteki üç tam boss savaşına yeni, zaman içinde hareket eden Lodestone koreografileri eklendi.

## Pale Gambler

Başının üzerinde altın-mor, eğimli bir rulet takımyıldızı döner. On iki elmas biçimi küçük parçacıklarla havada çizilir. El değişimleri üç eğimli yörünge halinde açılır. Kart, halka, yıldız yağmuru ve diğer darbe işaretleri yükselen kıvrımlı ışık izleri oluşturur. All In, Last Deal, faz kırılması ve zafer daha geniş, katmanlı dalgalarla vurgulanır. Darbe efektleri mevcut sunucu sinematik paketinden tetiklenir.

## Ashen Curator

Fildişi-kor renginde, ters yönlerde dönen iki arşiv sarmalı gövdenin üst çevresinde ilerler. Tehlikeler etkinleşince konumlarından ayrık kül/kıvılcım sütunları yükselir. Bölüm değişimleri, açık kitap ve çöküş anları havada genişleyen katmanlarla vurgulanır. Efektler eşzamanlanan tehlike kimliklerini ve uyarı zamanlarını izler; geç katılınca eski darbelerin tekrar oynatılması sınırlandırılır.

## Mourning Notary

Başının üzerinde gümüş ve mühür kızılı ışıklardan büyük bir terazi asılıdır; iki zincir ve terazi kefesi parçacıklarla hacim kazanır. Mühür darbeleri, üçlü mühür çağlayanı ve birleşik hüküm saldırıları gerçek saldırı saatlerine bağlı yükselen izler üretir. Çan hükümleri üst üste açılan cenaze dalgalarıyla vurgulanır. Yeni faz, Unbound ve çöküşte daha geniş halkalar açılır.

## Okunabilirlik ve yük sınırı

Yeni süslemeler ağırlıklı olarak yerden yüksekte bulunur; mevcut yerdeki saldırı sınırları ve güvenli alanlar korunur. Yeni katman kamera sarsıntısı veya tam ekran flaş eklemez. Kameranın 2,5 blok yakınına parçacık doğurmaz.

Yeni katman en yakın üç bossu, 64 blok mesafede izler. Bir kare yerine oyun tick'i üzerinden ilerler. Aynı anda en fazla 64 kısa efekt saklar; parçacık bütçesi normal ayarda tick başına 220, azaltılmış ayarda 110, minimum ayarda 48'dir. Wardbound azaltılmış hareket seçeneğinde bütçe 32 olur; dönüş ve parçacık hareketi azaltılır. Bu sayılar yalnızca yeni katmana aittir; temel paketin mevcut efektlerini kapsamaz. Dünya değişince kayıtlar temizlenir; oyun duraklayınca ilerleme durur. Curator parçacık ayrıntısı kapalıysa onun yeni katmanı da kapalıdır.

## Doğrulama ve kullanım

Tam Java kaynak derlemesi yerel Forge 47.4.20 / Java 17 / GeckoLib ve Lodestone bağımlılıklarıyla geçti (45 uyarı). 99 zamanlama kontrolü; atlanan tick, tekrarlanan ağ güncellemesi, eski darbe, saat sıfırlanması ve üçlü çağlayan örneklerini kapsıyor. Sunucu boss kodu, hasar, ganimet, kayıt ve ağ protokolü temel arşivle aynı bırakıldı.

Bu ZIP tam kaynak projedir, doğrudan mods klasörüne konacak JAR değildir. ForgeGradle ile `gradlew.bat build` çalıştırılarak paketlenmelidir. Gradle yeniden eşleme/paketleme, gerçek Minecraft istemci testi, çok oyunculu görsel kontrol ve FPS ölçümü bu ortamda yapılmadı. Oyunda özellikle Notary üçlü mühür saldırısı, Curator sığınak okunabilirliği ve Gambler All In sırasında görsel yoğunluk kontrol edilmelidir.

Yeni dosyalar: `client/MasterSpectacleFx.java`, `client/MasterFxTiming.java`. Gambler'ın mevcut istemci efekt işleyicisine tek bir çağrı eklendi. Sürüm 39.5.0 olarak işaretlendi. Yeni bitmap doku gerekmez; hacim, Lodestone parçacıklarının üç boyutlu yörüngelerinden oluşur.
