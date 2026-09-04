import cv2
from PIL import Image, ImageOps

prefix = "src/main/resources/assets/mekanismheated/textures/block/models"

def make_blue_hsv(input_path, output_path, brightness_boost=1.2):
    img = Image.open(input_path).convert("RGBA")
    hsv_img = img.convert("RGB").convert("HSV")
    h, s, v = hsv_img.split()
    target_blue_hue = int(240 * (255 / 360))
    h = h.point(lambda _: target_blue_hue)
    v = v.point(lambda i: min(255, int(i * brightness_boost)))
    hsv_modified = Image.merge("HSV", (h, s, v)).convert("RGB")
    r_new, g_new, b_new = hsv_modified.split()
    _, _, _, a_orig = img.split()

    final_img = Image.merge("RGBA", (r_new, g_new, b_new, a_orig))
    final_img.save(output_path)

for x in ["ports_large_led.png", "ports_led.png"]:
    make_blue_hsv(f"{prefix}/{x}", f"{prefix}/condenser_{x}")
