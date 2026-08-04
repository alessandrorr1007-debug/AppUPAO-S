import os
from PIL import Image

src_image_path = r"C:\Users\RODRIGUEZ\.gemini\antigravity\brain\226c1b51-62a1-419c-9c82-fabf1b9cf49c\upao_app_icon_1785697191514.jpg"
res_dir = r"C:\Users\RODRIGUEZ\AndroidStudioProjects\UPAOS\app\src\main\res"

sizes = {
    "mipmap-mdpi": (48, 108),
    "mipmap-hdpi": (72, 162),
    "mipmap-xhdpi": (96, 216),
    "mipmap-xxhdpi": (144, 324),
    "mipmap-xxxhdpi": (192, 432)
}

img = Image.open(src_image_path).convert("RGBA")

for folder, (ic_size, fore_size) in sizes.items():
    target_folder = os.path.join(res_dir, folder)
    os.makedirs(target_folder, exist_ok=True)
    
    # Eliminar todos los archivos .webp para evitar conflictos con aapt2
    for file_name in os.listdir(target_folder):
        if file_name.endswith(".webp"):
            os.remove(os.path.join(target_folder, file_name))

    # Guardar iconos en formato PNG consistente
    img_ic = img.resize((ic_size, ic_size), Image.Resampling.LANCZOS)
    img_ic.save(os.path.join(target_folder, "ic_launcher.png"), "PNG")
    img_ic.save(os.path.join(target_folder, "ic_launcher_round.png"), "PNG")

    img_fore = img.resize((fore_size, fore_size), Image.Resampling.LANCZOS)
    img_fore.save(os.path.join(target_folder, "ic_launcher_foreground.png"), "PNG")

print("Iconos limpios generados exclusivamente en PNG.")
