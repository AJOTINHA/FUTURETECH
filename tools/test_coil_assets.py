"""Check actual baked face positions for coplanar overlapping polygons.

Run: python -B -m unittest discover -s tools -p test_coil_assets.py
"""
import itertools
import math
import unittest

from coil_assets import coil_model

EPS = 1e-7


def faces(model):
    for element in model['elements']:
        x, y, z = element['from']
        X, Y, Z = element['to']
        corners = {
            'north': [(x,y,z),(X,y,z),(X,Y,z),(x,Y,z)],
            'south': [(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)],
            'east': [(X,y,z),(X,y,Z),(X,Y,Z),(X,Y,z)],
            'west': [(x,y,z),(x,y,Z),(x,Y,Z),(x,Y,z)],
            'up': [(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],
            'down': [(x,y,z),(X,y,z),(X,y,Z),(x,y,Z)],
        }
        for side in element['faces']:
            points = corners[side]
            if 'rotation' in element:
                r = element['rotation']
                assert r['axis'] == 'y' and not r.get('rescale', False)
                ox, _, oz = r['origin']
                c, s = math.cos(math.radians(r['angle'])), math.sin(math.radians(r['angle']))
                points = [(ox+(a-ox)*c+(d-oz)*s,b,oz-(a-ox)*s+(d-oz)*c) for a,b,d in points]
            a,b,c = points[:3]
            u,v = [b[i]-a[i] for i in range(3)], [c[i]-a[i] for i in range(3)]
            n = [u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
            length = math.sqrt(sum(t*t for t in n))
            n = [t/length for t in n]
            dominant = max(range(3), key=lambda i: abs(n[i]))
            if n[dominant] < 0: n = [-t for t in n]
            distance = sum(n[i]*a[i] for i in range(3))
            yield element['name']+':'+side, points, n, distance, dominant


def area(polygon):
    return sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(polygon,polygon[1:]+polygon[:1]))/2


def overlap_area(first, second):
    """Convex polygon clipping; touching edges have zero area and are allowed."""
    if area(first) < 0: first = first[::-1]
    if area(second) < 0: second = second[::-1]
    result = first
    for a,b in zip(second,second[1:]+second[:1]):
        def side(p): return (b[0]-a[0])*(p[1]-a[1])-(b[1]-a[1])*(p[0]-a[0])
        old, result = result, []
        if not old: return 0
        for p,q in zip(old,old[1:]+old[:1]):
            dp,dq = side(p),side(q)
            if dp >= -EPS: result.append(p)
            if (dp > EPS and dq < -EPS) or (dp < -EPS and dq > EPS):
                t = dp/(dp-dq)
                result.append(tuple(p[i]+t*(q[i]-p[i]) for i in range(2)))
    return abs(area(result)) if result else 0


def conflicts(model):
    found = []
    for a,b in itertools.combinations(faces(model),2):
        if a[0].split(':')[0] == b[0].split(':')[0]: continue
        if abs(a[3]-b[3]) > EPS or any(abs(x-y)>EPS for x,y in zip(a[2],b[2])): continue
        axes = [i for i in range(3) if i != a[4]]
        p,q = [[tuple(point[i] for i in axes) for point in face[1]] for face in (a,b)]
        if overlap_area(p,q) > EPS: found.append((a[0],b[0]))
    return found


class CoilGeometryTest(unittest.TestCase):
    def test_detector_distinguishes_overlap_from_touching_edges(self):
        square = [(0,0),(1,0),(1,1),(0,1)]
        self.assertAlmostEqual(overlap_area(square,[(.5,0),(1.5,0),(1.5,1),(.5,1)]),.5)
        self.assertAlmostEqual(overlap_area(square,[(1,0),(2,0),(2,1),(1,1)]),0)

    def test_no_faces_compete_for_the_same_plane(self):
        self.assertEqual(conflicts(coil_model()), [])


if __name__ == '__main__':
    unittest.main()
