
/* Author : Stephen Smalley, <sds@epoch.ncsc.mil> */

/* FLASK */

/* 
 * Implementation of the extensible bitmap type.
 */

#include <stdlib.h>

#include <sepol/policydb/ebitmap.h>
#include <sepol/policydb/policydb.h>

#include "debug.h"
#include "private.h"

int ebitmap_or(ebitmap_t * dst, const ebitmap_t * e1, const ebitmap_t * e2)
{
	ebitmap_node_t *n1, *n2, *new, *prev;

	ebitmap_init(dst);

	n1 = e1->node;
	n2 = e2->node;
	prev = 0;
	while (n1 || n2) {
		new = (ebitmap_node_t *) malloc(sizeof(ebitmap_node_t));
		if (!new) {
			ebitmap_destroy(dst);
			return -ENOMEM;
		}
		memset(new, 0, sizeof(ebitmap_node_t));
		if (n1 && n2 && n1->startbit == n2->startbit) {
			new->startbit = n1->startbit;
			new->map = n1->map | n2->map;
			n1 = n1->next;
			n2 = n2->next;
		} else if (!n2 || (n1 && n1->startbit < n2->startbit)) {
			new->startbit = n1->startbit;
			new->map = n1->map;
			n1 = n1->next;
		} else {
			new->startbit = n2->startbit;
			new->map = n2->map;
			n2 = n2->next;
		}

		new->next = 0;
		if (prev)
			prev->next = new;
		else
			dst->node = new;
		prev = new;
	}

	dst->highbit = (e1->highbit > e2->highbit) ? e1->highbit : e2->highbit;
	return 0;
}

int ebitmap_union(ebitmap_t * dst, const ebitmap_t * e1)
{
	ebitmap_t tmp;

	if (ebitmap_or(&tmp, dst, e1))
		return -1;
	ebitmap_destroy(dst);
	dst->node = tmp.node;
	dst->highbit = tmp.highbit;

	return 0;
}

int ebitmap_and(ebitmap_t *dst, ebitmap_t *e1, ebitmap_t *e2)
{
	unsigned int i, length = min(ebitmap_length(e1), ebitmap_length(e2));
	ebitmap_init(dst);
	for (i=0; i < length; i++) {
		if (ebitmap_get_bit(e1, i) && ebitmap_get_bit(e2, i)) {
			int rc = ebitmap_set_bit(dst, i, 1);
			if (rc < 0)
				return rc;
		}
	}
	return 0;
}

int ebitmap_xor(ebitmap_t *dst, ebitmap_t *e1, ebitmap_t *e2)
{
	unsigned int i, length = max(ebitmap_length(e1), ebitmap_length(e2));
	ebitmap_init(dst);
	for (i=0; i < length; i++) {
		int val = ebitmap_get_bit(e1, i) ^ ebitmap_get_bit(e2, i);
		int rc = ebitmap_set_bit(dst, i, val);
		if (rc < 0)
			return rc;
	}
	return 0;
}

int ebitmap_not(ebitmap_t *dst, ebitmap_t *e1, unsigned int maxbit)
{
	unsigned int i;
	ebitmap_init(dst);
	for (i=0; i < maxbit; i++) {
		int val = ebitmap_get_bit(e1, i);
		int rc = ebitmap_set_bit(dst, i, !val);
		if (rc < 0)
			return rc;
	}
	return 0;
}

int ebitmap_andnot(ebitmap_t *dst, ebitmap_t *e1, ebitmap_t *e2, unsigned int maxbit)
{
	ebitmap_t e3;
	ebitmap_init(dst);
	int rc = ebitmap_not(&e3, e2, maxbit);
	if (rc < 0)
		return rc;
	rc = ebitmap_and(dst, e1, &e3);
	ebitmap_destroy(&e3);
	if (rc < 0)
		return rc;
	return 0;
}

unsigned int ebitmap_cardinality(ebitmap_t *e1)
{
	unsigned int i, count = 0;
	for (i=ebitmap_startbit(e1); i < ebitmap_length(e1); i++)
		if (ebitmap_get_bit(e1, i))
			count++;
	return count;
}

int ebitmap_hamming_distance(ebitmap_t * e1, ebitmap_t * e2)
{
	if (ebitmap_cmp(e1, e2))
		return 0;
	ebitmap_t tmp;
	int rc = ebitmap_xor(&tmp, e1, e2);
	if (rc < 0)
		return -1;
	int distance = ebitmap_cardinality(&tmp);
	ebitmap_destroy(&tmp);
	return distance;
}

int ebitmap_cmp(const ebitmap_t * e1, const ebitmap_t * e2)
{
	ebitmap_node_t *n1, *n2;

	if (e1->highbit != e2->highbit)
		return 0;

	n1 = e1->node;
	n2 = e2->node;
	while (n1 && n2 &&
	       (n1->startbit == n2->startbit) && (n1->map == n2->map)) {
		n1 = n1->next;
		n2 = n2->next;
	}

	if (n1 || n2)
		return 0;

	return 1;
}

int ebitmap_cpy(ebitmap_t * dst, const ebitmap_t * src)
{
	ebitmap_node_t *n, *new, *prev;

	ebitmap_init(dst);
	n = src->node;
	prev = 0;
	while (n) {
		new = (ebitmap_node_t *) malloc(sizeof(ebitmap_node_t));
		if (!new) {
			ebitmap_destroy(dst);
			return -ENOMEM;
		}
		memset(new, 0, sizeof(ebitmap_node_t));
		new->startbit = n->startbit;
		new->map = n->map;
		new->next = 0;
		if (prev)
			prev->next = new;
		else
			dst->node = new;
		prev = new;
		n = n->next;
	}

	dst->highbit = src->highbit;
	return 0;
}

int ebitmap_contains(const ebitmap_t * e1, const ebitmap_t * e2)
{
	ebitmap_node_t *n1, *n2;

	if (e1->highbit < e2->highbit)
		return 0;

	n1 = e1->node;
	n2 = e2->node;
	while (n1 && n2 && (n1->startbit <= n2->startbit)) {
		if (n1->startbit < n2->startbit) {
			n1 = n1->next;
			continue;
		}
		if ((n1->map & n2->map) != n2->map)
			return 0;

		n1 = n1->next;
		n2 = n2->next;
	}

	if (n2)
		return 0;

	return 1;
}

int ebitmap_get_bit(const ebitmap_t * e, unsigned int bit)
{
	ebitmap_node_t *n;

	if (e->highbit < bit)
		return 0;

	n = e->node;
	while (n && (n->startbit <= bit)) {
		if ((n->startbit + MAPSIZE) > bit) {
			if (n->map & (MAPBIT << (bit - n->startbit)))
				return 1;
			else
				return 0;
		}
		n = n->next;
	}

	return 0;
}

int ebitmap_set_bit(ebitmap_t * e, unsigned int bit, int value)
{
	ebitmap_node_t *n, *prev, *new;
	uint32_t startbit = bit & ~(MAPSIZE - 1);
	uint32_t highbit = startbit + MAPSIZE;

	if (highbit == 0) {
		ERR(NULL, "bitmap overflow, bit 0x%x", bit);
		return -EINVAL;
	}

	prev = 0;
	n = e->node;
	while (n && n->startbit <= bit) {
		if ((n->startbit + MAPSIZE) > bit) {
			if (value) {
				n->map |= (MAPBIT << (bit - n->startbit));
			} else {
				n->map &= ~(MAPBIT << (bit - n->startbit));
				if (!n->map) {
					/* drop this node from the bitmap */

					if (!n->next) {
						/*
						 * this was the highest map
						 * within the bitmap
						 */
						if (prev)
							e->highbit =
							    prev->startbit +
							    MAPSIZE;
						else
							e->highbit = 0;
					}
					if (prev)
						prev->next = n->next;
					else
						e->node = n->next;

					free(n);
				}
			}
			return 0;
		}
		prev = n;
		n = n->next;
	}

	if (!value)
		return 0;

	new = (ebitmap_node_t *) malloc(sizeof(ebitmap_node_t));
	if (!new)
		return -ENOMEM;
	memset(new, 0, sizeof(ebitmap_node_t));

	new->startbit = startbit;
	new->map = (MAPBIT << (bit - new->startbit));

	if (!n) {
		/* this node will be the highest map within the bitmap */
		e->highbit = highbit;
	}

	if (prev) {
		new->next = prev->next;
		prev->next = new;
	} else {
		new->next = e->node;
		e->node = new;
	}

	return 0;
}

void ebitmap_destroy(ebitmap_t * e)
{
	ebitmap_node_t *n, *temp;

	if (!e)
		return;

	n = e->node;
	while (n) {
		temp = n;
		n = n->next;
		free(temp);
	}

	e->highbit = 0;
	e->node = 0;
	return;
}

int ebitmap_read(ebitmap_t * e, void *fp)
{
	int rc;
	ebitmap_node_t *n, *l;
	uint32_t buf[3], mapsize, count, i;
	uint64_t map;

	ebitmap_init(e);

	rc = next_entry(buf, fp, sizeof(uint32_t) * 3);
	if (rc < 0)
		goto bad;

	mapsize = le32_to_cpu(buf[0]);
	e->highbit = le32_to_cpu(buf[1]);
	count = le32_to_cpu(buf[2]);

	if (mapsize != MAPSIZE) {
		printf
		    ("security: ebitmap: map size %d does not match my size %zu (high bit was %d)\n",
		     mapsize, MAPSIZE, e->highbit);
		goto bad;
	}
	if (!e->highbit) {
		e->node = NULL;
		goto ok;
	}
	if (e->highbit & (MAPSIZE - 1)) {
		printf
		    ("security: ebitmap: high bit (%d) is not a multiple of the map size (%zu)\n",
		     e->highbit, MAPSIZE);
		goto bad;
	}
	l = NULL;
	for (i = 0; i < count; i++) {
		rc = next_entry(buf, fp, sizeof(uint32_t));
		if (rc < 0) {
			printf("security: ebitmap: truncated map\n");
			goto bad;
		}
		n = (ebitmap_node_t *) malloc(sizeof(ebitmap_node_t));
		if (!n) {
			printf("security: ebitmap: out of memory\n");
			rc = -ENOMEM;
			goto bad;
		}
		memset(n, 0, sizeof(ebitmap_node_t));

		n->startbit = le32_to_cpu(buf[0]);

		if (n->startbit & (MAPSIZE - 1)) {
			printf
			    ("security: ebitmap start bit (%d) is not a multiple of the map size (%zu)\n",
			     n->startbit, MAPSIZE);
			goto bad_free;
		}
		if (n->startbit > (e->highbit - MAPSIZE)) {
			printf
			    ("security: ebitmap start bit (%d) is beyond the end of the bitmap (%zu)\n",
			     n->startbit, (e->highbit - MAPSIZE));
			goto bad_free;
		}
		rc = next_entry(&map, fp, sizeof(uint64_t));
		if (rc < 0) {
			printf("security: ebitmap: truncated map\n");
			goto bad_free;
		}
		n->map = le64_to_cpu(map);

		if (!n->map) {
			printf
			    ("security: ebitmap: null map in ebitmap (startbit %d)\n",
			     n->startbit);
			goto bad_free;
		}
		if (l) {
			if (n->startbit <= l->startbit) {
				printf
				    ("security: ebitmap: start bit %d comes after start bit %d\n",
				     n->startbit, l->startbit);
				goto bad_free;
			}
			l->next = n;
		} else
			e->node = n;

		l = n;
	}

      ok:
	rc = 0;
      out:
	return rc;
      bad_free:
	free(n);
      bad:
	if (!rc)
		rc = -EINVAL;
	ebitmap_destroy(e);
	goto out;
}

/* FLASK */
