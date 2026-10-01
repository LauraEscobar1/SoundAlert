import { Module } from '@nestjs/common';
import { DevicesModule } from '../devices/devices.module.js';
import { RulesModule } from '../rules/rules.module.js';
import { DetectionsController } from './detections.controller.js';
import { DetectionsService } from './detections.service.js';

@Module({
  imports: [DevicesModule, RulesModule],
  controllers: [DetectionsController],
  providers: [DetectionsService],
})
export class DetectionsModule {}
