# -*- coding: utf-8 -*-
# Copyright 2010-2018, Google Inc.
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are
# met:
#
#     * Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
#     * Redistributions in binary form must reproduce the above
# copyright notice, this list of conditions and the following disclaimer
# in the documentation and/or other materials provided with the
# distribution.
#     * Neither the name of Google Inc. nor the names of its
# contributors may be used to endorse or promote products derived from
# this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
# "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
# LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
# A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
# LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
# THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

"""Generate emoji data file (.java)

Generated .java file is used by Android version.
"""

__author__ = "yoichio"

from collections import defaultdict
import logging
import optparse
import re
import sys

from build_tools import code_generator_util

# Table to convert categories.
# "offset" will be added to each emoji index to keep the order.
# We assign 100,000 and greater values for carrier emoji, so "offset" should be
# less than 100,000.
_CATEGORY_MAP = {
  'SMILEY_PEOPLE': {'category': 'FACE', 'offset': 0},
  'ANIMALS_NATURE': {'category': 'FOOD', 'offset': 0},
  'FOOD_DRINK': {'category': 'FOOD', 'offset': 10000},
  'TRAVEL_PLACES': {'category': 'CITY', 'offset': 0},
  'ACTIVITY': {'category': 'ACTIVITY', 'offset': 0},
  'OBJECTS': {'category': 'ACTIVITY', 'offset': 10000},
  'SYMBOLS': {'category': 'NATURE', 'offset': 0},
  'FLAGS': {'category': 'NATURE', 'offset': 10000},
}
_CATEGORY_LIST = list(set(
    [entry['category'] for entry in _CATEGORY_MAP.values()]))


def ReadData(stream):
  category_map = defaultdict(list)
  stream = code_generator_util.SkipLineComment(stream)
  stream = code_generator_util.ParseColumnStream(stream, delimiter='\t')
  stream = code_generator_util.SelectColumn(stream, [0, 2, 8, 9, 10, 11, 12])
  for (code, pua_code, japanese_name, docomo_name, softbank_name, kddi_name,
       category_index) in stream:
    if bool(code) != bool(japanese_name):
      if code:
        logging.fatal('No Japanese name for %s found.' % code)
      else:
        logging.fatal('No Unicode code point for %s found.' % japanese_name)
      sys.exit(-1)
    if not code:
      # Use dummy code point
      code = '0'
    if not pua_code:
      # Use dummy code point
      pua_code = '0'
    if pua_code[0] == '>':
      # Don't skip entires which has non-primary PUA codepoint since they also
      # has unique Unicode codepoint.
      # e.g. "BLACK SQUARE BUTTON" and "LARGE BLUE CIRCLE"
      pua_code = pua_code[1:]

    code_values = [int(c, 16) for c in re.split(r' +', code.strip())]
    pua_code_value = int(pua_code, 16)
    (category, index) = category_index.split('-')
    index = int(index) + _CATEGORY_MAP[category]['offset']
    category = _CATEGORY_MAP[category]['category']
    category_map[category].append(
        (index, code_values, pua_code_value,
         japanese_name, docomo_name, softbank_name, kddi_name))
  return category_map


_CHARACTER_NORMALIZE_MAP = {
    'Ａ': 'A',
    'Ｂ': 'B',
    'Ｃ': 'C',
    'Ｄ': 'D',
    'Ｅ': 'E',
    'Ｆ': 'F',
    'Ｇ': 'G',
    'Ｈ': 'H',
    'Ｉ': 'I',
    'Ｊ': 'J',
    'Ｋ': 'K',
    'Ｌ': 'L',
    'Ｍ': 'M',
    'Ｎ': 'N',
    'Ｏ': 'O',
    'Ｐ': 'P',
    'Ｑ': 'Q',
    'Ｒ': 'R',
    'Ｓ': 'S',
    'Ｔ': 'T',
    'Ｕ': 'U',
    'Ｖ': 'V',
    'Ｗ': 'W',
    'Ｘ': 'X',
    'Ｙ': 'Y',
    'Ｚ': 'Z',

    'ａ': 'a',
    'ｂ': 'b',
    'ｃ': 'c',
    'ｄ': 'd',
    'ｅ': 'e',
    'ｆ': 'f',
    'ｇ': 'g',
    'ｈ': 'h',
    'ｉ': 'i',
    'ｊ': 'j',
    'ｋ': 'k',
    'ｌ': 'l',
    'ｍ': 'm',
    'ｎ': 'n',
    'ｏ': 'o',
    'ｐ': 'p',
    'ｑ': 'q',
    'ｒ': 'r',
    'ｓ': 's',
    'ｔ': 't',
    'ｕ': 'u',
    'ｖ': 'v',
    'ｗ': 'w',
    'ｘ': 'x',
    'ｙ': 'y',
    'ｚ': 'z',

    '０': '0',
    '１': '1',
    '２': '2',
    '３': '3',
    '４': '4',
    '５': '5',
    '６': '6',
    '７': '7',
    '８': '8',
    '９': '9',

    '（': '(',
    '）': ')',
}


def PreprocessName(name):
  if not name:
    return 'null'
  name = ''.join(_CHARACTER_NORMALIZE_MAP.get(c, c) for c in name)
  name = name.replace('(', '\\n(')
  return '"%s"' % name


def OutputData(category_map, stream):
  for data_list in category_map.values():
    data_list.sort()

  stream.write('package sh.eliza.japaneseinput.emoji;\n'
               'public class EmojiData {\n')

  for category in _CATEGORY_LIST:
    # The content of data list is
    # 0: Index in the category
    # 1: Code points of Unicode emoji
    # 2: Code point of carrier emoji
    # 3: Japanese Unicode emoji name
    # 4: DOCOMO carrier emoji name
    # 5: Softbank carrier emoji name
    # 6: KDDI carrier emoji name
    data_list = [c for c in category_map[category]
                 if c[3] or c[4] or c[5] or c[6]]
    stream.write(
        '  public static final String[] %s_VALUES = new String[]{\n' %
        category)
    for _, codes, pua_code, japanese, docomo, softbank, kddi in data_list:
      stream.write('    %s,\n' % code_generator_util.ToJavaStringLiteral(codes))
    stream.write('  };\n')

    stream.write(
        '  public static final String[] %s_PUA_VALUES = new String[]{\n' %
        category)
    for _, codes, pua_code, japanese, docomo, softbank, kddi in data_list:
      stream.write(
          '    %s,\n' % code_generator_util.ToJavaStringLiteral(pua_code))
    stream.write('  };\n')

    stream.write(
        '  public static final String[] UNICODE_%s_NAME = {\n' % category)
    for _, codes, pua_code, japanese, docomo, softbank, kddi in data_list:
      stream.write('    %s, \n' % PreprocessName(japanese))
    stream.write('  };\n')

    stream.write(
        '  public static final String[] DOCOMO_%s_NAME = {\n' % category)
    for _, codes, pua_code, japanese, docomo, softbank, kddi in data_list:
      stream.write('    %s, \n' % PreprocessName(docomo))
    stream.write('  };\n')

    stream.write(
        '  public static final String[] SOFTBANK_%s_NAME = {\n' % category)
    for _, codes, pua_code, japanese, docomo, softbank, kddi in data_list:
      stream.write('    %s, \n' % PreprocessName(softbank))
    stream.write('  };\n')

    stream.write(
        '  public static final String[] KDDI_%s_NAME = {\n' % category)
    for _, codes, pua_code, japanese, docomo, softbank, kddi in data_list:
      stream.write('    %s, \n' % PreprocessName(kddi))
    stream.write('  };\n')

  stream.write('}\n')


def ParseOptions():
  parser = optparse.OptionParser()
  parser.add_option('--emoji_data', dest='emoji_data',
                    help='Path to emoji_data.tsv')
  parser.add_option('--output', dest='output', help='Output file name')
  return parser.parse_args()[0]


def main():
  options = ParseOptions()
  with open(options.emoji_data) as stream:
    emoji_data = ReadData(stream)

  with open(options.output, 'w') as stream:
    OutputData(emoji_data, stream)


if __name__ == '__main__':
  main()
