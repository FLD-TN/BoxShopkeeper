# BoxShopkeeper

Menu chế tạo / đổi đồ (`/chetao`): người chơi đưa nguyên liệu (và tiền, nếu có) để đổi lấy vật phẩm. Admin dựng toàn bộ
shop và các giao dịch ngay trong game bằng giao diện, không cần sửa file.

Paper 1.21.1, Java 21. Không có phụ thuộc bắt buộc. Vault, PlayerPoints, BoxCore (Gem) và MMOItems có thì dùng.

## Cách dùng

- `/chetao` mở danh sách shop. Mỗi shop có icon, tên, mô tả và thứ tự hiển thị riêng.
- Trong shop: chuột trái để đổi 1 lần, Shift + chuột trái để đổi nhiều nhất có thể (tối đa `trade.max-per-click`),
  chuột phải để xem chi tiết nguyên liệu và thành phẩm.
- Nguyên liệu phải khớp hoàn toàn với mẫu (cùng loại, cùng dữ liệu, chỉ khác số lượng). Không có kiểu so khớp "gần
  giống".
- Một giao dịch có tối đa 9 nguyên liệu, kèm các loại tiền làm chi phí: `vault` ($), `points` (PlayerPoints) và `gem`
  (BoxCore).
- Trước khi trừ đồ, plugin tính trước xem túi đồ có đủ chỗ cho thành phẩm không. Tiền bị trừ trước; nếu bước nào hỏng
  giữa chừng thì hoàn lại hết.
- Shop có thể yêu cầu quyền `chetao.shop.<tên shop>` (bật trong trình chỉnh sửa).

## Chỉnh sửa trong game

`/chetao admin` mở danh sách shop để tạo, đổi tên, mô tả, icon, thứ tự, bật quyền, xoá. Trong một shop, thêm giao
dịch bằng cách đặt nguyên liệu vào ô 3x3 bên trái và thành phẩm vào ô bên phải. Muốn thêm tiền vào chi phí thì tạo
token bằng `/chetao currency <loại> <số tiền>` rồi đặt token vào ô nguyên liệu.

Khi template MMOItems thay đổi, `/chetao update-mmoitems <shop|all>` dựng lại mọi item MMOItems trong shop theo bản mới
và giữ nguyên số lượng. Thêm `preview` để xem trước sẽ đổi những gì. Item không còn trong MMOItems thì giữ nguyên và
được báo lại.

## Dữ liệu

- `shops/<id>.yml`: mỗi shop một file, item lưu dạng Base64.
- Backup tự động mỗi lần sửa (tối đa `backups.keep-per-shop` bản, cách nhau ít nhất `min-interval-seconds`).
- `logs/trades-<ngày>.log`: mỗi giao dịch thành công một dòng, giữ `logging.keep-days` ngày.

## Lệnh

| Lệnh | Quyền | Mô tả |
|---|---|---|
| `/chetao` | `chetao.use` | Mở menu chế tạo |
| `/chetao open <tên>` | `chetao.use` | Mở thẳng một shop |
| `/chetao open <tên> <người chơi>` | `chetao.admin` | Mở shop cho người khác (dùng cho NPC) |
| `/chetao admin` | `chetao.admin` | Trình chỉnh sửa |
| `/chetao create\|edit\|delete <tên>` | `chetao.admin` | Tạo, sửa, xoá shop |
| `/chetao list` | `chetao.admin` | Liệt kê shop |
| `/chetao currency <loại> <số tiền>` | `chetao.admin` | Tạo token tiền cho trình chỉnh sửa |
| `/chetao update-mmoitems <tên\|all> [preview]` | `chetao.admin` | Cập nhật item MMOItems |
| `/chetao reload` | `chetao.admin` | Đọc lại config, messages và shop |

## Build

```
./gradlew build
```

Ra file `build/libs/BoxShopkeeper-<version>.jar`. Repo chỉ chứa mã nguồn, không có jar nào ngoài
`gradle/wrapper/gradle-wrapper.jar` của Gradle. Trước khi build, đặt vào thư mục `libs/`:

- `MMOItems-6.10.jar`: jar plugin MMOItems
- `MythicLib-1.7.jar`: jar plugin MythicLib
