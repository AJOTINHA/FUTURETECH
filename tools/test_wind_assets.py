"""Validate actual wind turbine model geometry and referenced assets."""
import json
import unittest
from generate_solar_generator import ASSETS
from test_coil_assets import conflicts

class WindAssetsTest(unittest.TestCase):
    def test_models_have_no_coplanar_overlap_and_resolve_all_textures(self):
        models=list((ASSETS/'models/block/wind_generator').glob('*.json'))
        self.assertEqual(len(models),13)
        for path in models:
            model=json.loads(path.read_text())
            self.assertEqual(conflicts(model),[],path.name)
            for texture in model['textures'].values():
                self.assertTrue((ASSETS/('textures/'+texture.split(':')[1]+'.png')).is_file(),texture)
            for element in model['elements']:
                for low,high in zip(element['from'],element['to']):
                    self.assertTrue(0<=low<high<=16,(path.name,element['name']))

    def test_items_use_the_shared_three_blade_renderer(self):
        item=json.loads((ASSETS/'items/wind_generator.json').read_text())['model']
        entries=[item['fallback']]+[c['model'] for c in item['cases']]
        self.assertEqual({e['model']['mk'] for e in entries},{1,2,3,4})
        for entry in entries:
            self.assertEqual(entry['type'],'minecraft:special')
            self.assertEqual(entry['model']['type'],'futuretech:wind_turbine')
            self.assertTrue((ASSETS/('models/'+entry['base'].split(':')[1]+'.json')).is_file())

if __name__=='__main__': unittest.main()
