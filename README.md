flowchart TD
    A["Kamera Görüntüsü<br/>(MainActivity)"] --> B["ROI'ye göre kırpma<br/>cropToRoi()"]
    B --> C["ResultActivity'ye<br/>pendingBitmap aktarımı"]
    C --> D["Letterbox ölçekleme<br/>1024×1024 + dolgu"]
    D --> E["RGB → normalize float<br/>bitmapToByteBuffer()"]
    E --> F["TFLite Inference<br/>interpreter.run()"]
    F --> G["Çıktı: [1,10,21504] tensör"]
    G --> H["parseOutput()<br/>skor eşiği + koordinat düzeltme"]
    H --> I["applyNMS()<br/>çakışan kutuları eleme"]
    I --> J["List&lt;Detection&gt;"]
    J --> K["BoxOverlayView<br/>görselleştirme"]
    J --> L["CombinationChecker<br/>sütun ayrımı + sıralama"]
    L --> M["18 elemanlı dizi"]
    M --> N{"Veritabanı<br/>karşılaştırması"}
    N -->|Eşleşme var| O["OK (yeşil)"]
    N -->|Eşleşme yok| P["NOK (kırmızı)"]
    O --> Q["FuseTableDialog<br/>(opsiyonel inceleme)"]
    P --> Q
