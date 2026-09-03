from PIL import Image

def convert_white_to_transparent(image_path, output_path, threshold=240):
    img = Image.open(image_path).convert("RGBA")
    data = img.getdata()

    new_data = []
    for item in data:
        if item[0] >= threshold and item[1] >= threshold and item[2] >= threshold:
            new_data.append((255, 255, 255, 0))
        else:
            new_data.append(item)

    img.putdata(new_data)
    img.save(output_path, "PNG")

prefix = "src/main/resources/assets/mekanismheated/textures/item/"
tiers = ["advanced", "basic", "elite", "ultimate"]
for t in tiers:
    path = f"{prefix}fused_pipe_{t}.png"
    convert_white_to_transparent(path, path)