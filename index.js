/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

import path from 'path';
import fs from 'fs';
import { platform } from 'os';

export default function (kibana) {
  return new kibana.Plugin({
    id: 'java-langserver',
    require: ['elasticsearch', 'kibana'],
    name: 'java-langserver',
    config(Joi) {
      return Joi.object({
        enabled: Joi.boolean().default(true),
      }).default();
    },
    init(server) {
      const codeDataPath = path.resolve(server.config().get('path.data'), 'code');
      const jdtConfigPath = path.resolve(codeDataPath, 'jdt_config');
      const configFilePath = path.resolve(jdtConfigPath, 'config.ini');
      if(!fs.existsSync(codeDataPath)) {
        fs.mkdirSync(codeDataPath);
      }
      if (!fs.existsSync(jdtConfigPath)) {
        fs.mkdirSync(jdtConfigPath);
      }
      try {
        fs.lstatSync(configFilePath);
        fs.unlinkSync(configFilePath);
      } catch (e) {}
      const osPlatform = platform();
      if (osPlatform == 'darwin') {
        fs.symlinkSync(path.resolve(__dirname, 'lib/repository/config_mac/config.ini'), configFilePath);
      } else if (osPlatform == 'linux') {
        fs.symlinkSync(path.resolve(__dirname, 'lib/repository/config_linux/config.ini'), configFilePath);
      }
      server.expose('install', {
        path: path.join(__dirname, 'lib'),
      });
    }
  });
}
