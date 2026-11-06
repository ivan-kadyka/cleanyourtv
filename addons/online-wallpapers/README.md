# Online wallpapers

Nature and landscape photos from the internet as the launcher's wallpaper, each with its place and photographer
under the top-right buttons, like the Windows lock screen. Turned on in **Launcher settings > Online wallpapers**.

- **Sources** (both free, no key): Bing's image of the day (`HPImageArchive`, the last ~15 days across the English
  markets) and Windows Spotlight (`fd.api.iris.microsoft.com`, random landscapes). Days showing interiors or
  buildings are skipped with a short word list (`Bing.NOT_NATURE`).
- **Remote:** on Home, ⏪ shows the previous picture and ⏩ the next; ⏩ past the last one fetches a new Spotlight
  batch and shows its first picture.
- **Cache:** at most 30 pictures, stored at 1920x1080 (the size this TV draws its screen at; Spotlight's 4K
  pictures are halved once). The launcher reports each picture it shows; past 30, the least recently shown go.
- **Refresh:** Bing at most every 12 hours, only when Home asks; no background service, nothing at boot.
- **Effects:** none while online wallpapers are on: no dim, no app-colour glow, no sky, no cinematic artwork.

The launcher never goes online: it copies the add-on's pictures into its own `online/` folder through the add-on's
read-only provider (`content://tv.cleanyour.wallpapers.pictures/pictures`, calls `more` and `used`).
The Online wallpapers tile opens a status screen with a Refresh button. Permissions: `INTERNET` only.

The pictures are Microsoft's and their photographers' (© in each caption), for personal use as a wallpaper.
