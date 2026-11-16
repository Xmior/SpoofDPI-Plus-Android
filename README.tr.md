<p align="center">
  <img src="SpoofDPIPlus-1024.png" width="128" alt="SpoofDPI+">
</p>

<h1 align="center">SpoofDPI+ Android</h1>

<p align="center">
  Android'de DPI (Derin Paket İnceleme) engellerini aşın: VPN yok, root yok, bilgisayar yok.<br>
  <a href="README.md">English</a> · <b>Türkçe</b>
</p>

> **macOS sürümü:** [SpoofDPI+ macOS](https://github.com/Xmior/SpoofDPI-Plus-MacOS)

---

## Özellikler

- **Tek dokunuşla aç/kapat**: uygulamadan, bildirimden veya hızlı ayarlar kutucuğundan.
- **VPN değildir.** Telefonunuzda küçük bir proxy çalışır. Trafik başka bir sunucuya uğramadan doğrudan internete gider.
- **Kalıcı bildirim menüsü**: durum ile **Başlat/Durdur**, **Wi-Fi ayarları** ve **Tamamen kapat** düğmeleri.
- **Durum paneli**: proxy servisi, port 8080, Wi-Fi proxy ayarı ve erişim testi (discord.com).
- **Güvenlik uyarısı**: Wi-Fi proxy'si ayarlıyken SpoofDPI+ kapalıysa sizi uyarır.
- **Açılışta otomatik başlar** (kapanmadan önce açıksa).
- **Türkçe / İngilizce.** Dil cihaz diline göre seçilir: Türkçe cihazlarda Türkçe, diğer tüm dillerde İngilizce.
- Açık ve koyu tema. Yaklaşık 200 KB.

## Gereksinimler

- **Android 8.0 (Oreo, API 26) veya üstü**
- **Wi-Fi** bağlantısı (bkz. [Sınırlamalar](#sınırlamalar))

Samsung Galaxy M31 (Android 12) ve Android 15 emülatöründe denendi.

## Kurulum

1. [Releases](../../releases) sayfasından `SpoofDPIPlus-<sürüm>-Android.apk` dosyasını indirin.
2. Telefonda açıp yükleyin. İstenirse bilinmeyen kaynaklardan yükleme izni verin.
3. **SpoofDPI+**'ı açın ve **Başlat**'a basın. İstenirse bildirim izni verin.
4. **Ayarlar → Wi-Fi** bölümünde ağınızın yanındaki dişliye dokunun ve **Proxy → Manuel** seçin:

   | Alan | Değer |
   |---|---|
   | Ana makine adı | `127.0.0.1` |
   | Bağlantı noktası | `8080` |

5. Kaydedin. Durum panelindeki dört satır da yeşil olmalı.
6. (Önerilen) **Arka planda çalışmaya izin ver** düğmesine basın; pil tasarrufu uygulamayı durdurmasın.

4. adımı kullandığınız her Wi-Fi ağı için bir kez yapın.

### Hızlı ayarlar kutucuğu

Bildirim panelini aşağı çekin, kalem (düzenle) simgesine dokunun ve **SpoofDPI+** kutucuğunu etkin kutucuklara sürükleyin.

## Nasıl çalışır?

```
Uygulama (tarayıcı, Discord...)
        │  HTTP / HTTPS
        ▼
Wi-Fi proxy'si ──► SpoofDPI+ (127.0.0.1:8080, ön plan servisi)
                       │  1. DNS'i DNS-over-HTTPS ile çözer (1.1.1.1)
                       │  2. TLS ClientHello'nun ilk 40 baytını
                       │     2 ms arayla tek tek gönderir
                       ▼
                    İnternet
```

Engelleme iki aşamada yapılıyor:

1. **DNS zehirleme.** ISS'nin DNS sunucusu engelli siteler için sahte bir IP döndürür. SpoofDPI+ alan adlarını DNS-over-HTTPS ile çözer; ISS sorguyu göremez ve değiştiremez.
2. **SNI filtreleme.** Her HTTPS bağlantısının ilk paketinde (TLS ClientHello) site adı (SNI) şifresiz yer alır. DPI bu adı okuyup bağlantıyı sıfırlar. Testlerimizde DPI'ın yaklaşık 20 küçük paketten sonra incelemeyi bıraktığı görüldü. Bu yüzden SpoofDPI+ ilk 40 baytı 1 baytlık ayrı paketler halinde, kalanını tek parça gönderir. Site adı geldiğinde DPI artık bakmıyordur. Sunucu parçaları birleştirip normal şekilde yanıt verir.

**Neden 2 ms bekleme?** Android'in içindeki Linux çekirdeği, `TCP_NODELAY` açık olsa bile art arda yazılan küçük verileri tek pakette birleştirir (TCP autocorking). Bekleme olmadan 1 baytlık parçalar telefondan tek paket olarak çıkar ve site engelli kalır. Masaüstü SpoofDPI'daki düz "1 baytlık parçalama" yöntemi bu yüzden Android'de çalışmaz.

Yalnızca her bağlantının ilk paketi parçalanır. Sonrasında veri normal akar, fark edilir bir yavaşlama olmaz.

## Sınırlamalar

- **Yalnızca Wi-Fi.** VPN veya root olmadan Android, uygulamaların mobil veri için proxy ayarlamasına izin vermez.
- **Wi-Fi proxy'si ayarlıyken SpoofDPI+ kapalıysa o Wi-Fi'da internet olmaz.** Ya SpoofDPI+'ı açık tutun ya da Wi-Fi ayarlarından Proxy'yi **Yok** yapın. Uygulama ve bildirim bu durumda sizi uyarır.
- **Bazı uygulamalar sistem proxy'sini yok sayar.** Tarayıcılar ve çoğu uygulama uyar; bazı oyun ve uygulamalar uymaz.
- **Discord sesli sohbeti** UDP kullanır. HTTP proxy UDP taşıyamadığı için sesli kanallar bağlanmayabilir.

## Sorun giderme

| Sorun | Çözüm |
|---|---|
| Wi-Fi'da internet yok | SpoofDPI+ kapalı. **Başlat**'a basın ya da Wi-Fi proxy'sini **Yok** yapın. |
| Uygulama arka planda kapanıyor | **Arka planda çalışmaya izin ver**'e basın. Samsung'da Ayarlar → Pil → Arka plan kullanım sınırları'nı da kontrol edin. |
| Site hâlâ açılmıyor | Durum panelinin tamamen yeşil olduğundan emin olun, sonra uygulamadaki **Günlükler**'e bakın. |
| "Wi-Fi proxy'si: Ayarlı değil" | Bağlı olduğunuz ağda ana makine adının `127.0.0.1`, portun `8080` olduğunu kontrol edin. |

## Kaynaktan derleme

Gerekenler: JDK 17+, Android SDK (platform 35, build-tools 35.0.0).

```bash
./gradlew assembleRelease
```

Çıktı: `app/build/outputs/apk/release/SpoofDPIPlus-<sürüm>-Android.apk`. Sürüm numarası `VERSION` dosyasından okunur.

### Proje yapısı

```
app/src/main/java/com/xmior/spoofdpiplus/
  ProxyServer.java    DPI aşan HTTP proxy (CONNECT + ClientHello parçalama)
  Dns.java            DNS-over-HTTPS çözücü (1.1.1.1, önbellekli)
  ProxyService.java   Ön plan servisi ve bildirim menüsü
  ToggleTile.java     Hızlı ayarlar kutucuğu
  BootReceiver.java   Açılışta yeniden başlatma
  MainActivity.java   Durum paneli, düğmeler, Hakkında ve Yardım
  Logs.java           Uygulama içi günlük
app/src/main/res/values/strings.xml      İngilizce (varsayılan)
app/src/main/res/values-tr/strings.xml   Türkçe
```

## Teşekkürler

- [Xmior/SpoofDPI-Plus-MacOS](https://github.com/Xmior/SpoofDPI-Plus-MacOS): macOS sürümü

- [xvzc/SpoofDPI](https://github.com/xvzc/SpoofDPI): DPI aşma yöntemi
- [cagritaskn/SplitWire-Turkey](https://github.com/cagritaskn/SplitWire-Turkey): ilham kaynağı

**Caferius/Xmior** tarafından.

## Uyarı

Bu araç yalnızca bilgi erişimi özgürlüğü amacıyla sunulmaktadır. Kullanımından doğan sorumluluk kullanıcıya aittir.
