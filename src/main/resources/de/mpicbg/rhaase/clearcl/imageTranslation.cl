
__kernel void translate_image_2F(
        __read_only image2d_t input,
        __private int translation_x,
        __private int translation_y,
        __write_only image2d_t output
)
{
    const sampler_t sampler = CLK_NORMALIZED_COORDS_FALSE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_NEAREST;

    int2 pos = {get_global_id(0), get_global_id(1)};

    int x = -translation_x;
    int y = -translation_y;

    float sum = read_imagef(input, sampler, pos + (int2)( x, y )).x;

    float4 pix = {sum,0,0,0};
	write_imagef(output, pos, pix);
}


