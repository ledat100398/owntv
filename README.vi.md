<div align="center">

# Glass Home

**Màn hình chính tối giản, mang tinh thần Apple TV, cho Android TV và Google TV.**

Không quảng cáo, không hàng gợi ý, không app chạy ngầm. Chỉ có app của bạn, đồng hồ và kính mờ.

[English](README.md) · Tiếng Việt · [简体中文](README.zh-CN.md)

</div>

---

## Mục lục

- [Vì sao có dự án này](#vì-sao-có-dự-án-này)
- [Tính năng](#tính-năng)
- [Cần những gì](#cần-những-gì)
- [Cài trên Windows](#cài-trên-windows)
- [Cài thủ công (mọi hệ điều hành)](#cài-thủ-công-mọi-hệ-điều-hành)
- [Cách dùng](#cách-dùng)
- [Tắt app chạy ngầm](#tắt-app-chạy-ngầm)
- [Tùy chỉnh](#tùy-chỉnh)
- [Trả lại như cũ](#trả-lại-như-cũ)
- [Thiết bị đã thử](#thiết-bị-đã-thử)
- [Quyền riêng tư](#quyền-riêng-tư)
- [Đóng góp](#đóng-góp)
- [Ghi nhận](#ghi-nhận)
- [Giấy phép](#giấy-phép)

## Vì sao có dự án này

Màn hình chính gốc của Google TV giống một tủ kính bày hàng: hàng nội dung được tài trợ, trailer tự phát,
cùng hàng chục dịch vụ của hãng khởi động theo tivi và chạy suốt. Glass Home thay nó bằng một màn hình
chỉ làm tốt một việc: đưa bạn tới đúng app cần mở, nhanh và yên tĩnh.

## Tính năng

**Thiết kế**
- Thanh kính mờ phía trên gồm Nguồn vào, Wi-Fi, Cài đặt. Một giọt nước trượt giữa các nút theo nhịp lò
  xo nhẹ; các nút không bao giờ đổi kích thước nên giao diện không giật.
- App mở ra bằng hiệu ứng phóng từ chính ô vừa bấm và thu về đúng ô khi quay lại. Hiệu ứng được vẽ
  từng khung, viền bo khử răng cưa, không chớp trắng, không vỡ hình.
- Ảnh nền trôi nhẹ theo ô đang chọn tạo chiều sâu. Mỗi lần chuyển có tiếng "tách" nhẹ, tới mép thì rung
  nhẹ, theo đúng cài đặt âm thanh hệ thống của tivi.
- Dùng phông Inter: gần với SF Pro của Apple và hiển thị tiếng Việt đầy đủ.

**Dùng hằng ngày**
- Bảng Nguồn vào liệt kê các cổng HDMI kèm tên thiết bị đang cắm (qua HDMI-CEC), cùng Truyền hình.
- Bảng Wi-Fi cho biết đang nối mạng gì và có vào được Internet không.
- Bảng Cài đặt gồm những mục hay dùng: Hình ảnh, Âm thanh, Hẹn giờ tắt, Kiểu màn hình chính, Tất cả
  cài đặt. Mục nào tivi không mở được sẽ tự ẩn.
- Tùy chọn ngày giờ cỡ lớn bên trái (Cài đặt › Màn hình chính).
- Giữ OK trên một app để dời, ẩn hoặc xem thông tin. Trái/phải đi như đọc một dòng chữ: hết hàng thì
  sang hàng kế; ở app cuối bấm phải thì về app đầu.
- Màn hình chờ đồng hồ trôi chậm để bảo vệ tấm nền.
- Giao diện tiếng Việt, tiếng Anh và tiếng Trung giản thể, tự chọn theo ngôn ngữ của tivi.

**Hiệu năng**
- Không làm mờ trực tiếp: lớp kính dùng một bản ảnh nền nhỏ đã làm mờ sẵn.
- Bóng của ô app vẽ một lần rồi dùng lại; chuyển động chỉ đổi vị trí và độ trong.
- Có script đo thời gian mở, số khung hình bị giật và bộ nhớ, so với ngưỡng mục tiêu.

## Cần những gì

- Tivi hoặc box chạy **Android TV hoặc Google TV 8.0 trở lên** (Sony, TCL, Xiaomi, Philips, Hisense,
  NVIDIA Shield, Chromecast with Google TV...). Tivi Samsung (Tizen) và LG (webOS) không chạy Android
  nên không dùng được.
- Bật **Tùy chọn cho nhà phát triển** và gỡ lỗi qua mạng (xem bên dưới).
- Một máy tính cùng mạng với tivi. Các file bấm-là-chạy dành cho **Windows 10/11**; trên macOS hoặc
  Linux thì làm theo phần cài thủ công.

## Cài trên Windows

1. **Bật tùy chọn nhà phát triển trên tivi.** Cài đặt › Hệ thống › Giới thiệu › bấm OK vào *Bản dựng*
   7 lần. Sau đó Cài đặt › Hệ thống › Tùy chọn cho nhà phát triển › bật *Gỡ lỗi USB* (và *Gỡ lỗi qua
   mạng* nếu tivi có). Xem IP của tivi trong Cài đặt › Mạng.
2. **Tải kho này về** (nút xanh *Code* › *Download ZIP*) rồi giải nén.
3. **Chạy `3-CAI-LAUNCHER.cmd`.** Lần đầu sẽ tự tải Java 17 và bộ dựng Android (khoảng 600 MB, 10–20 phút);
   các lần sau chỉ vài giây. Khi tivi hỏi *Cho phép gỡ lỗi USB?*, tick *Luôn cho phép* rồi đồng ý.
   Glass Home sẽ nằm trong hàng ứng dụng; nút Home vẫn về màn gốc để bạn dùng thử trước.
4. **Đặt làm màn hình chính:** chạy `4-DAT-LAM-MAN-CHINH.cmd`. Script tự bấm Home để kiểm tra, nếu không
   ra Glass Home thì tự trả lại như cũ.

| Script | Việc làm |
|---|---|
| `1-TOI-UU-TV.cmd` | Tắt app chạy ngầm, quảng cáo, theo dõi (danh sách cho Sony), hỏi từng nhóm |
| `2-KHOI-PHUC.cmd` | Trả lại mọi thứ bước 1 đã đổi |
| `3-CAI-LAUNCHER.cmd` | Dựng và cài Glass Home, đặt màn hình chờ đồng hồ |
| `4-DAT-LAM-MAN-CHINH.cmd` | Đặt Glass Home làm màn hình chính |
| `5-TRA-LAI-MAN-GOC.cmd` | Trả lại màn hình chính gốc |
| `6-DO-HIEU-NANG.cmd` | Đo thời gian mở, khung hình giật, bộ nhớ |
| `7-KIEM-TRA.cmd` | Chạy thử mọi thao tác remote trên tivi thật, rồi bấm ngẫu nhiên 1.500 phím; báo lỗi văng app và bộ nhớ |

Script nào cũng hỏi trước khi đổi, có sao lưu và có script trả lại tương ứng.

## Cài thủ công (mọi hệ điều hành)

Cần [ADB](https://developer.android.com/tools/releases/platform-tools) và JDK 17.

```sh
# dựng
cd launcher
./gradlew assembleDebug            # Windows: gradlew.bat assembleDebug

# cài
adb connect <IP_TIVI>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Đặt làm màn hình chính:

```sh
adb shell cmd package set-home-activity --user 0 io.github.ledat100398.tvhome/.MainActivity
```

Trên **Google TV**, màn gốc sẽ giành lại nút Home; còn khi màn gốc vắng mặt thì trình cài đặt ban đầu lại
chiếm nút Home để "sửa". Gỡ cả hai khỏi người dùng hiện tại (không xóa gì khỏi tivi):

```sh
adb shell pm uninstall -k --user 0 com.google.android.apps.tv.launcherx
adb shell pm uninstall -k --user 0 com.google.android.tungsten.setupwraith
adb shell cmd package set-home-activity --user 0 io.github.ledat100398.tvhome/.MainActivity
adb shell input keyevent KEYCODE_HOME
```

Trong thời gian này không thêm được tài khoản Google mới trên tivi; cần thì trả lại trước (xem
[Trả lại như cũ](#trả-lại-như-cũ)).

Màn hình chờ đồng hồ (tùy chọn):

```sh
adb shell settings put secure screensaver_components io.github.ledat100398.tvhome/.ClockDream
```

## Cách dùng

| Trên điều khiển | Kết quả |
|---|---|
| Phím mũi tên | Chuyển giữa các app. Hết hàng thì sang hàng kế |
| Phải ở app cuối | Về app đầu |
| Lên ở hàng đầu | Lên thanh trên |
| OK | Mở app hoặc mở bảng |
| Giữ OK, hoặc phím Menu | Dời, Ẩn, Thông tin app |
| Khi đang dời app | Mũi tên để dời, OK để đặt, Back để hủy |
| Back | Đóng bảng, hủy dời, rời thanh trên, hoặc về hàng đầu |

App đã ẩn hiện lại trong Cài đặt › Hiện lại app đã ẩn.

## Tắt app chạy ngầm

`1-TOI-UU-TV.cmd` dùng danh sách [`examples/sony-bravia-vn/goi-tat.tsv`](examples/sony-bravia-vn/goi-tat.tsv),
chia nhóm (quảng cáo và theo dõi, chế độ trưng bày, gửi báo lỗi, app ít dùng, giọng nói, chiếu màn hình)
và hỏi từng nhóm. App được gỡ **chỉ khỏi người dùng hiện tại** bằng `pm uninstall -k --user 0`: bản gốc
vẫn nằm trong tivi, trả lại bằng `cmd package install-existing`. Không dùng cách *tắt* app vì tivi Sony
tự bật lại app bị tắt mỗi lần khởi động lại.

Với hãng khác, thư mục [`examples/`](examples/) có danh sách đã thử cho TCL, Xiaomi, Hisense, OnePlus,
BenQ, Sanyo, NVIDIA... Xem kỹ danh sách rồi chạy từng dòng dạng `adb shell pm uninstall -k --user 0 <gói>`.

Tuyệt đối không gỡ: app Truyền hình, dịch vụ HDMI/CEC, dịch vụ điều khiển và bàn phím, Google Play
Services, CH Play, Bluetooth.

## Tùy chỉnh

Tất cả nằm trong [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml):

- `dock_apps`: các app đứng đầu, theo thứ tự.
- `hidden_apps`: các gói không bao giờ hiện thành ô.
- `live_tv_apps`, `sleep_timer_apps`: app Truyền hình và app hẹn giờ tắt của tivi.

Gói nào không có trên tivi sẽ được bỏ qua, nên cấu hình mặc định an toàn với mọi tivi. Muốn đổi ảnh nền,
thay `launcher/app/src/main/res/drawable-nodpi/wallpaper.jpg` (2008 × 1130) rồi dựng lại.

## Trả lại như cũ

- Màn hình chính gốc: chạy `5-TRA-LAI-MAN-GOC.cmd`, hoặc

  ```sh
  adb shell cmd package install-existing com.google.android.tungsten.setupwraith
  adb shell cmd package install-existing com.google.android.apps.tv.launcherx
  adb shell input keyevent KEYCODE_HOME
  ```

- App chạy ngầm: chạy `2-KHOI-PHUC.cmd`, hoặc `adb shell cmd package install-existing <gói>`.
- Gỡ Glass Home: Cài đặt › Ứng dụng › Glass Home › Gỡ cài đặt.

Dùng ổn rồi thì tắt Gỡ lỗi USB trên tivi cho an toàn.

## Thiết bị đã thử

| Thiết bị | Hệ điều hành | Tình trạng |
|---|---|---|
| Sony BRAVIA XR-65X95J | Google TV, Android 12 | Dùng hằng ngày |

Glass Home chỉ dùng API công khai của Android TV nên chạy được trên mọi thiết bị Android TV 8.0 trở lên.
Nếu bạn thử trên mẫu khác, hãy mở issue ghi tên mẫu và kết quả để bảng này đầy dần.

## Quyền riêng tư

Glass Home không kết nối mạng và không có bất kỳ phần thu thập dữ liệu nào. App chỉ xin quyền vị trí vì
Android bắt buộc quyền này mới cho đọc tên mạng Wi-Fi đang nối.

## Đóng góp

Báo lỗi, danh sách app cho tivi khác và pull request đều được hoan nghênh; xem [CONTRIBUTING.md](CONTRIBUTING.md).

## Ghi nhận

Glass Home phát triển từ [tv-vibehack](https://github.com/Gathin23/tv-vibehack) của Gathin. Ý tưởng thay
màn hình chính qua ADB, công cụ ADB trong `tools/` và các danh sách app do cộng đồng đóng góp trong
`examples/` đến từ dự án đó. Phần launcher, thiết kế và các script Windows được viết lại từ đầu.

Phông chữ: [Inter](https://rsms.me/inter/) của Rasmus Andersson (SIL Open Font License).
Biểu tượng bánh răng theo [Lucide](https://lucide.dev) (ISC).

## Giấy phép

[MIT](LICENSE) © 2026 Lê Trí Thành Đạt, kèm ghi nhận bản quyền gốc của tv-vibehack © 2026 Gathin.
