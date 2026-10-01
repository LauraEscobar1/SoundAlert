import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Put,
} from '@nestjs/common';
import {
  ApiBadRequestResponse,
  ApiCreatedResponse,
  ApiNoContentResponse,
  ApiNotFoundResponse,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';
import { DevicesService } from './devices.service.js';
import { toContextDto } from '../catalog/catalog.mapper.js';
import {
  ChangeContextDto,
  DeviceContextDto,
  DeviceDto,
  RegisterDeviceDto,
  UpdateDeviceDto,
} from './dto/device.dto.js';

@ApiTags('devices')
@ApiNotFoundResponse({
  description: 'Dispositivo (o usuario propietario) no encontrado',
})
@ApiBadRequestResponse({ description: 'Datos o deviceId (UUID) inválidos' })
@Controller('devices')
export class DevicesController {
  constructor(private readonly service: DevicesService) {}

  @Post()
  @ApiOperation({ summary: 'Registrar un reloj/dispositivo' })
  @ApiCreatedResponse({ type: DeviceDto })
  async register(@Body() dto: RegisterDeviceDto): Promise<DeviceDto> {
    return this.service.toDto(await this.service.register(dto));
  }

  @Get(':deviceId')
  @ApiOperation({ summary: 'Obtener un dispositivo' })
  @ApiOkResponse({ type: DeviceDto })
  async get(@Param('deviceId', ParseUUIDPipe) id: string): Promise<DeviceDto> {
    return this.service.toDto(await this.service.getOrThrow(id));
  }

  @Patch(':deviceId')
  @ApiOperation({
    summary:
      'Actualizar datos y configuración (minConfidence, alertsEnabled...)',
  })
  @ApiOkResponse({ type: DeviceDto })
  async update(
    @Param('deviceId', ParseUUIDPipe) id: string,
    @Body() dto: UpdateDeviceDto,
  ): Promise<DeviceDto> {
    return this.service.toDto(await this.service.update(id, dto));
  }

  @Get(':deviceId/context')
  @ApiOperation({ summary: 'Consultar el contexto actual del dispositivo' })
  @ApiOkResponse({ type: DeviceContextDto })
  async getContext(
    @Param('deviceId', ParseUUIDPipe) id: string,
  ): Promise<DeviceContextDto> {
    const device = await this.service.getOrThrow(id);
    return { deviceId: device.id, ...toContextDto(device.currentContext) };
  }

  @Put(':deviceId/context')
  @ApiOperation({
    summary: 'Cambiar el contexto actual (casa, calle, universidad, trabajo)',
  })
  @ApiOkResponse({ type: DeviceDto })
  async changeContext(
    @Param('deviceId', ParseUUIDPipe) id: string,
    @Body() dto: ChangeContextDto,
  ): Promise<DeviceDto> {
    return this.service.toDto(
      await this.service.changeContext(id, dto.currentContext),
    );
  }

  @Delete(':deviceId')
  @HttpCode(204)
  @ApiOperation({ summary: 'Eliminar un dispositivo y todo su historial' })
  @ApiNoContentResponse()
  async remove(@Param('deviceId', ParseUUIDPipe) id: string): Promise<void> {
    await this.service.remove(id);
  }
}
