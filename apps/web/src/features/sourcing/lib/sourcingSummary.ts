const countOf = (count: number, one: string, many: string) => `${count} ${count === 1 ? one : many}`;

export const companiesOf = (count: number) => countOf(count, "company", "companies");

export const peopleOf = (count: number) => countOf(count, "executive", "executives");
