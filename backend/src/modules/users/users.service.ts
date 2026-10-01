import { Injectable, NotFoundException } from '@nestjs/common';
import { User } from '../../database/entities.js';
import { UsersRepository } from '../../database/repositories.js';
import { CreateUserDto } from './dto/user.dto.js';

@Injectable()
export class UsersService {
  constructor(private readonly users: UsersRepository) {}

  create(dto: CreateUserDto): Promise<User> {
    return this.users.create({ name: dto.name, email: dto.email ?? null });
  }

  async getOrThrow(id: string): Promise<User> {
    const user = await this.users.findById(id);
    if (!user) throw new NotFoundException(`Usuario ${id} no encontrado`);
    return user;
  }
}
