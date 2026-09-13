import dev.futuretech.client.BatterySphereMesh;
import java.util.Locale;

/** Exports the same immutable mesh used in the block renderer for the offline preview. */
class ExportMesh {
    public static void main(String[] args) {
        var mesh = BatterySphereMesh.create();
        System.out.print("{\"nodes\":[");
        for (int i=0;i<mesh.nodes().size();i++) {
            var p=mesh.nodes().get(i);
            System.out.printf(Locale.ROOT, "%s[%.9f,%.9f,%.9f]", i==0?"":",",p.x(),p.y(),p.z());
        }
        System.out.print("],\"edges\":[");
        for(int i=0;i<mesh.edges().size();i++) {
            var e=mesh.edges().get(i);
            System.out.printf("%s[%d,%d]",i==0?"":",",e.a(),e.b());
        }
        System.out.print("]}");
    }
}
