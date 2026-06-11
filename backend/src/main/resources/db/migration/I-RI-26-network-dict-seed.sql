-- MOD-03 / RI-26: 网络查源字典种子 (百度百科 + 维基百科, PM D-2)
USE archive_db;

INSERT IGNORE INTO dict_type (type_code, type_name, is_system, sort_order, enabled) VALUES
('network_dict_source', '网络查源', 1, 9, 1);

INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order, enabled) VALUES
('network_dict_source', 'baidu_baike',
 '{"baseUrl":"https://baike.baidu.com/api/openapi","timeout":5000}',
 0, 1, 1, 1),
('network_dict_source', 'wikipedia_zh',
 '{"baseUrl":"https://zh.wikipedia.org/w/api.php","timeout":5000}',
 0, 1, 2, 1);
