export type Turno = 'MANHA' | 'TARDE' | 'NOITE' | 'INTEGRAL';

export const TURNOS: { valor: Turno; rotulo: string }[] = [
    { valor: 'MANHA', rotulo: 'Manhã' },
    { valor: 'TARDE', rotulo: 'Tarde' },
    { valor: 'NOITE', rotulo: 'Noite' },
    { valor: 'INTEGRAL', rotulo: 'Integral'},
];

export interface Turma {
    id: number;
    nome: string;
    turno: Turno;
    anoLetivo: number;
}

export type TurmaRequest = Omit<Turma, 'id'>;