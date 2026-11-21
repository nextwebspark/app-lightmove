import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button, Field, Input, Modal } from "../../../components/ui";

export const DASHBOARD_NAME_LIMIT = 80;

const nameSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "Give the dashboard a name.")
    .max(DASHBOARD_NAME_LIMIT, `Keep the name to ${DASHBOARD_NAME_LIMIT} characters.`),
});

type NameValues = z.infer<typeof nameSchema>;

/** Names a new dashboard, or renames one. */
export function DashboardNameDialog({
  title,
  subtitle,
  submitLabel,
  initialName = "",
  pending,
  onSubmit,
  onClose,
}: {
  title: string;
  subtitle?: string;
  submitLabel: string;
  initialName?: string;
  pending: boolean;
  onSubmit: (name: string) => void;
  onClose: () => void;
}) {
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<NameValues>({ resolver: zodResolver(nameSchema), defaultValues: { name: initialName } });
  const length = watch("name")?.length ?? 0;
  return (
    <Modal
      open
      onClose={onClose}
      title={title}
      subtitle={subtitle}
      onSubmit={handleSubmit((values) => onSubmit(values.name))}
      footer={
        <>
          <Button variant="secondary" type="button" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={pending}>
            {submitLabel}
          </Button>
        </>
      }
    >
      <Field label="Name" hint={`${length}/${DASHBOARD_NAME_LIMIT}`} error={errors.name?.message}>
        <Input autoFocus maxLength={DASHBOARD_NAME_LIMIT} placeholder="e.g. Weekly client update" {...register("name")} />
      </Field>
    </Modal>
  );
}
