using System;
using System.Drawing;

// Orthographic depth buffer: inset contacts must not disappear behind larger housing faces.
public static class CablePreviewRaster
{
    public static void Face(Bitmap output, double[] depths, Bitmap tile, double[] p, double light)
    {
        double ax=p[3]-p[0], ay=p[4]-p[1], bx=p[6]-p[0], by=p[7]-p[1];
        double determinant=ax*by-ay*bx;
        if (Math.Abs(determinant)<0.00001) return;
        double x3=p[3]+p[6]-p[0], y3=p[4]+p[7]-p[1];
        int minX=Math.Max(0,(int)Math.Floor(Math.Min(Math.Min(p[0],p[3]),Math.Min(p[6],x3))));
        int maxX=Math.Min(output.Width-1,(int)Math.Ceiling(Math.Max(Math.Max(p[0],p[3]),Math.Max(p[6],x3))));
        int minY=Math.Max(0,(int)Math.Floor(Math.Min(Math.Min(p[1],p[4]),Math.Min(p[7],y3))));
        int maxY=Math.Min(output.Height-1,(int)Math.Ceiling(Math.Max(Math.Max(p[1],p[4]),Math.Max(p[7],y3))));
        for(int y=minY;y<=maxY;y++) for(int x=minX;x<=maxX;x++)
        {
            double dx=x+0.5-p[0], dy=y+0.5-p[1];
            double u=(dx*by-dy*bx)/determinant, v=(ax*dy-ay*dx)/determinant;
            if(u<0 || u>=1 || v<0 || v>=1) continue;
            double depth=p[2]+u*(p[5]-p[2])+v*(p[8]-p[2]);
            int index=y*output.Width+x;
            if(depth<=depths[index]) continue;
            Color c=tile.GetPixel(Math.Min(tile.Width-1,(int)(u*tile.Width)),Math.Min(tile.Height-1,(int)(v*tile.Height)));
            if(c.A==0) continue;
            depths[index]=depth;
            output.SetPixel(x,y,Color.FromArgb(255,(int)(c.R*light),(int)(c.G*light),(int)(c.B*light)));
        }
    }
}
