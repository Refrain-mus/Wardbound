# Ashen Curator — Görünüş yenilemesi, 39.4.1

Bu sürüm, gönderdiğiniz oyun görüntüsündeki kaba gövde, iç içe giren sayfalar, düz PNG kitap ve bozuk boss panelini ele alır.

- **GeckoLib modeli yeniden kuruldu:** 29 kemik, 120 hacimli parça. Derin başlık ve içeride kalan seramik maske; katmanlı omuzluk; altı ayrı cüppe paneli; arkaya yerleştirilmiş pelerin; dirsek, bilek ve parmakları olan kollar.
- **1024 × 512 gerçek model kaplaması:** kumaş, brokar, el yazması, bakır, seramik ve deri için ayrı UV bölgeleri. ImageGen malzeme yüzeyleri üretti; model bir karakter resmini kamera önüne yerleştirerek çizilmiyor.
- **Göğüs kitabı gerçekten üç boyutlu:** deri kapak, metal çerçeve, sayfa bloğu ve ayrı menteşeler var. Hasar penceresinde kapaklar dışa açılıp açık kalır. Üstüne ikinci bir kitap PNG'si çizilmez.
- **12 animasyon yeniden düzenlendi:** kollar ve eller gövdeden uzak tutulur; omuz, dirsek ve bilek birlikte hareket eder. Giriş, geçiş, açık kitap ve çöküş animasyonları sürekli yeniden başlamaz; son poz tutulur.
- **Modelin önünü kapatan VFX kaldırıldı:** sürekli etrafında dönen büyük PNG sayfaları, gövdedeki dev kitap/cüppe resimleri ve büyük çöküş panosu yok. Dört fiziksel folyo omuzların arkasında durur.
- **Arena efektleri yeniden çizildi:** uzun saldırılara büyük parşömen resimleri esnetilmez. İnce hazırlık sınırı, daha belirgin hasar sınırı, düşük opaklıkta sıcak alan ve küçük arşiv işaretleri kullanılır. Korunmuş raflar dikdörtgen tehlike dolgusundan çıkarılır.
- **Lodestone ölçülü kullanılır:** beklemede gövde üstünde kıvılcım üretmez. Saldırı sırasında küçük kül parçaları bossun yaklaşık 4.5 blok uzağında ve yere yakın çıkar; ömürleri 14 tick'tir.
- **Boss paneli yeniden çizildi:** gerilmiş atlas resmi kaldırıldı; okunaklı, opak katalog paneli, sağlık çizgisi ve dört mühür göstergesi var. Efektler Minecraft'ın ortak çizim kuyruğunu topluca boşaltmaz; kendi çizim kuyruğunu kullanır.

## Kurulum

Teslim edilen ZIP **tam kaynak projedir**. Java 17 ile proje klasöründe `gradlew.bat build` çalıştırın. Yeni mod dosyasıyla eski Wardbound dosyasını değiştirin; iki sürümü birlikte yüklemeyin. Sunucu ve istemcide aynı sürümü kullanın. Model/kaplama değişimini görmek için Minecraft'ı yeniden başlatın.

Savaş mekaniği, ilerleme, ses bankası ve boss müziği korunur. `boss_theme.ogg` konumu aynıdır. Pale Gambler'a ait kod ve assetler değiştirilmedi.

Hızlı deneme: düz zeminde `/wardbound curator debug summon_force`. Modeli önden ve yandan kontrol edin; açık kitap penceresinde kapakların açık kalmasına, saldırıda kolların gövdeden ayrılmasına ve boss bar metninin temiz görünmesine bakın.

## Kontrol kapsamı

Yeni modelin gerçek JSON geometrisi, UV kaplaması ve animasyon pozları yazılımsal olarak render edilerek önden/yandan/açık kitap durumlarında incelendi. Teslimdeki önizleme **Minecraft ekran görüntüsü değildir**; pakete giren modelin geometri önizlemesidir.

Java kaynakları Forge 47.4.20 ve mevcut GeckoLib/Lodestone/PlayerAnimator bağımlılıklarıyla derleme kontrolünden geçti. UV sınırları, pozitif parça kalınlıkları, kemik bağları, animasyon hedefleri ve PNG alpha doğrulandı. Ayrıntılı sonuç `docs/validation/asset-checks.txt` dosyasında.

Bu oturumda Minecraft istemcisi çalıştırılmadı; shader paketleriyle görünüş ve oyun içi animasyon geçişleri canlı test edilmiş sayılmaz. Tam Gradle build/reobfuscation da burada çalıştırılmadı. Paket, kaynakların güncellenmiş ve derleme denetimi yapılmış halidir.

## Değişen ana dosyalar

```text
src/main/resources/assets/wardbound/geo/entity/ashen_curator.geo.json
src/main/resources/assets/wardbound/animations/entity/ashen_curator.animation.json
src/main/resources/assets/wardbound/textures/entity/ashen_curator.png
src/main/resources/assets/wardbound/textures/vfx/ashen_curator/archive_warning.png
src/main/resources/assets/wardbound/textures/vfx/ashen_curator/archive_heat.png
src/main/resources/assets/wardbound/textures/vfx/ashen_curator/archive_refuge.png
src/main/java/dev/marrowseal/wardbound/client/AshenCuratorTextureFx.java
src/main/java/dev/marrowseal/wardbound/client/CuratorBossBar.java
src/main/java/dev/marrowseal/wardbound/boss/AshenCuratorEntity.java
build.gradle
src/main/resources/META-INF/mods.toml
```

Görsel üretim: `tools/curator_visual_rework.py`; model önizlemesi: `tools/render_curator_mesh.py`. Malzeme kaynağı `docs/curator-art/material-source.png` içinde. Eski asset üretim betiği de yeni modelin üstüne eski modeli bırakmayacak şekilde güncellendi.

ImageGen için kullanılan istem: 4 sütun × 2 satır, boşluksuz UV malzeme atlası; üst sıra kömür siyahı kıvrımlı kumaş, kül grisi arşiv brokarı, ince el yazılı yaşlı parşömen ve kazınmış koyu bakır; alt sıra çatlak fildişi seramik, kabartmalı koyu deri kitap kapağı, eskimiş dikey kumaş kıvrımları ve koyu boşluk dokusu. Karakter resmi, perspektif veya parçacık görseli istenmedi. Çıktı gerçek model yüzeylerine UV ile eşlendi.
