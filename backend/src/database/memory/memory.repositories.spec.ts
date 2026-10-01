import { repositoryContract, RepositorySet } from '../repositories.contract.js';
import {
  MemoryAlertsRepository,
  MemoryDatabaseHealth,
  MemoryDetectionsRepository,
  MemoryDevicesRepository,
  MemoryRuleOverridesRepository,
  MemoryUsersRepository,
} from './memory.repositories.js';

describe('Repositorios en memoria (contrato)', () => {
  let repos: RepositorySet;
  beforeEach(() => {
    repos = {
      health: new MemoryDatabaseHealth(),
      users: new MemoryUsersRepository(),
      devices: new MemoryDevicesRepository(),
      rules: new MemoryRuleOverridesRepository(),
      detections: new MemoryDetectionsRepository(),
      alerts: new MemoryAlertsRepository(),
    };
  });

  repositoryContract(() => repos);
});
