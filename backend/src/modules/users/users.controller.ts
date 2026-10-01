import {
  Body,
  Controller,
  Get,
  Param,
  ParseUUIDPipe,
  Post,
} from '@nestjs/common';
import {
  ApiBadRequestResponse,
  ApiCreatedResponse,
  ApiNotFoundResponse,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';
import { DevicesService } from '../devices/devices.service.js';
import { DeviceDto } from '../devices/dto/device.dto.js';
import { CreateUserDto, UserDto } from './dto/user.dto.js';
import { UsersService } from './users.service.js';

@ApiTags('users')
@Controller('users')
export class UsersController {
  constructor(
    private readonly users: UsersService,
    private readonly devices: DevicesService,
  ) {}

  @Post()
  @ApiOperation({ summary: 'Crear un usuario (propietario de relojes)' })
  @ApiCreatedResponse({ type: UserDto })
  @ApiBadRequestResponse({ description: 'Datos inválidos' })
  create(@Body() dto: CreateUserDto): Promise<UserDto> {
    return this.users.create(dto);
  }

  @Get(':userId')
  @ApiOperation({ summary: 'Obtener un usuario' })
  @ApiOkResponse({ type: UserDto })
  @ApiNotFoundResponse({ description: 'Usuario no encontrado' })
  get(@Param('userId', ParseUUIDPipe) id: string): Promise<UserDto> {
    return this.users.getOrThrow(id);
  }

  @Get(':userId/devices')
  @ApiOperation({ summary: 'Dispositivos de un usuario' })
  @ApiOkResponse({ type: [DeviceDto] })
  @ApiNotFoundResponse({ description: 'Usuario no encontrado' })
  async devicesOf(
    @Param('userId', ParseUUIDPipe) id: string,
  ): Promise<DeviceDto[]> {
    await this.users.getOrThrow(id);
    return (await this.devices.listByOwner(id)).map((d) =>
      this.devices.toDto(d),
    );
  }
}
